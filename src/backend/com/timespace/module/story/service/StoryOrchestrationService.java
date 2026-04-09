package com.timespace.module.story.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.ai.agent.BaiguanAgent;
import com.timespace.module.ai.agent.JudgeAgent;
import com.timespace.module.ai.agent.ZhangyanAgent;
import com.timespace.module.ai.service.AIGatewayService;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.service.CardService;
import com.timespace.module.story.controller.StoryController.*;
import com.timespace.module.story.entity.*;
import com.timespace.module.story.mapper.StoryChapterMapper;
import com.timespace.module.story.mapper.StoryCharacterMapper;
import com.timespace.module.story.mapper.StoryMapper;
import com.timespace.module.story.mapper.StoryManuscriptMapper;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 故事编排服务 - AI Agent 协作核心
 *
 * 完整流程：
 *
 * 1. 开始新故事 (startStory)
 *    - 验证关键词卡所有权
 *    - 保存入局配置
 *    - 初始化配角
 *    - 调用说书人生成第一章
 *    - WebSocket 推送流式内容
 *
 * 2. 提交选择 (submitChoice / submitChoiceAndStream)
 *    - 验证章节归属
 *    - 保存用户选择
 *    - 触发 AI 协作流程
 *      1. 判官评估选择
 *      2. 更新故事上下文
 *      3. 说书人生成下一章（流式推送）
 *      4. 判官生成选项
 *      5. 更新关键词共鸣
 *    - 检查是否达到最大章节数
 *
 * 3. 结束生成手稿 (finishStory)
 *    - 验证故事状态
 *    - 调用说书人生成完整手稿
 *    - 调用稗官生成后日谈
 *    - 生成朱批
 *    - 更新用户统计数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryOrchestrationService extends ServiceImpl<StoryMapper, Story> {

    private final StoryMapper storyMapper;
    private final StoryChapterMapper chapterMapper;
    private final StoryCharacterMapper characterMapper;
    private final StoryManuscriptMapper manuscriptMapper;
    private final UserMapper userMapper;
    private final CardService cardService;
    private final AIGatewayService aiGatewayService;
    private final BaiguanAgent baiguanAgent;
    private final ZhangyanAgent zhangyanAgent;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${timespace.story.max-chapters:5}")
    private int maxChapters;

    @Value("${timespace.story.max-words:8000}")
    private int maxWords;

    // SSE 发射器存储（storyId -> SSE emitter）
    private final Map<Long, SseEmitter> storySseEmitters = new ConcurrentHashMap<>();

    // WebSocket 会话管理（userId -> WebSocket Session ID）
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

    // AI 流式任务线程池
    private final ExecutorService aiStreamExecutor = Executors.newFixedThreadPool(4);

    /**
     * 注册用户 WebSocket Session
     */
    public void registerSession(Long userId, String sessionId) {
        userSessionMap.put(userId, sessionId);
        log.info("WebSocket会话注册: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 注销用户 WebSocket Session
     */
    public void unregisterSession(Long userId) {
        userSessionMap.remove(userId);
        log.info("WebSocket会话注销: userId={}", userId);
    }

    /**
     * ========== SSE 流式 API ==========
     */

    /**
     * GET /api/story/{id}/stream
     * SSE 流式推送故事内容
     *
     * 返回 Flux<ServerSentEvent>，前端通过 EventSource 消费
     * 事件类型：
     *   - chapter_start  : 章节开始
     *   - chapter_chunk  : 章节内容片段
     *   - chapter_end    : 章节完成
     *   - judgment       : 判官判词
     *   - character_change: 配角命运变化
     *   - story_end      : 故事完成
     *   - error          : 错误信息
     */
    public SseEmitter storyStream(Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("SSE流式请求: userId={}, storyId={}", userId, storyId);

        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }

        SseEmitter emitter = new SseEmitter(0L);
        storySseEmitters.put(storyId, emitter);

        emitter.onCompletion(() -> { storySseEmitters.remove(storyId); });
        emitter.onTimeout(() -> { storySseEmitters.remove(storyId); });
        emitter.onError(e -> { storySseEmitters.remove(storyId); });

        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data("{\"storyId\":" + storyId + ",\"chapter\":" + story.getCurrentChapter() + "}"));
        } catch (Exception e) { log.warn("SSE send failed: {}", e.getMessage()); }

        return emitter;

    }

    /**
     * SSE 流式生成章节
     * 用于 /api/story/{id}/stream 路由，通过 StoryController 返回 Flux<ServerSentEvent>
     *
     * @param storyId 故事ID
     * @param chapterNo 章节号
     */
    public void generateChapterStream(Long storyId, Integer chapterNo) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("SSE章节流式生成: userId={}, storyId={}, chapterNo={}", userId, storyId, chapterNo);

        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            pushSseError(storyId, "STORY_NOT_FOUND");
            return;
        }

        List<KeywordCard> keywords = getKeywordCardsDetail(userId, story.getKeywordCardIds());
        List<StoryCharacter> characters = characterMapper.selectList(
                new LambdaQueryWrapper<StoryCharacter>()
                        .eq(StoryCharacter::getStoryId, storyId));

        // 获取上一章节（如果有）
        StoryChapter lastChapter = null;
        if (chapterNo > 1) {
            lastChapter = chapterMapper.selectOne(
                    new LambdaQueryWrapper<StoryChapter>()
                            .eq(StoryChapter::getStoryId, storyId)
                            .eq(StoryChapter::getChapterNo, chapterNo - 1));
        }

        // 获取当前章节（如果是续传）
        StoryChapter currentChapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<StoryChapter>()
                        .eq(StoryChapter::getStoryId, storyId)
                        .eq(StoryChapter::getChapterNo, chapterNo));

        // 流式生成
        try {
            String fullText = aiGatewayService.generateNextChapter(
                    story,
                    lastChapter,
                    lastChapter != null && currentChapter != null ?
                            findSelectedOption(currentChapter) : null,
                    keywords,
                    characters,
                    chunk -> {
                        // WebSocket 推送
                        pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                                .storyId(storyId)
                                .chapterNo(chapterNo)
                                .chunk(chunk)
                                .build());
                        // SSE 推送
                        pushSseChunk(storyId, chunk);
                    }
            ).getSceneText();

            // 章节完成
            storySseEmitters.get(storyId).send(
                    SseEmitter.event()
                            .name("chapter_end")
                            .data("{\"storyId\\\":" + storyId + ",\"chapterNo\":" + chapterNo + "}")
                            .build());

            // 同步保存章节
            if (currentChapter == null) {
                currentChapter = new StoryChapter();
                currentChapter.setStoryId(storyId);
                currentChapter.setChapterNo(chapterNo);
                currentChapter.setSceneText(fullText);
                // options 已在 AI 调用中设置
                chapterMapper.insert(currentChapter);
            }

            // 推送选项
            if (currentChapter.getOptions() != null) {
                storySseEmitters.get(storyId).send(
                        SseEmitter.event()
                                .name("options")
                                .data(toJson(currentChapter.getOptions()))
                                .build());
            }

        } catch (Exception e) {
            log.error("SSE章节生成失败: storyId={}, error={}", storyId, e.getMessage(), e);
            pushSseError(storyId, "AI生成失败: " + e.getMessage());
        }
    }

    /**
     * 判官评估后流式生成下一章
     * 用于 submitChoice 后的完整 AI 协作流程
     *
     * @param storyId 故事ID
     * @param chapterNo 当前章节号
     * @param optionId 选择的选项ID
     */
    public void submitChoiceAndStream(Long storyId, Integer chapterNo, Integer optionId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交选择并流式生成: userId={}, storyId={}, chapterNo={}, optionId={}",
                userId, storyId, chapterNo, optionId);

        try {
            // 1. 验证故事归属
            Story story = storyMapper.selectById(storyId);
            if (story == null || !story.getUserId().equals(userId)) {
                pushSseError(storyId, "STORY_NOT_FOUND");
                return;
            }
            if (story.getStatus() != 1) {
                pushSseError(storyId, "STORY_ENDED");
                return;
            }

            // 2. 获取当前章节并验证选项
            StoryChapter currentChapter = chapterMapper.selectOne(
                    new LambdaQueryWrapper<StoryChapter>()
                            .eq(StoryChapter::getStoryId, storyId)
                            .eq(StoryChapter::getChapterNo, chapterNo));
            if (currentChapter == null) {
                pushSseError(storyId, "CHAPTER_NOT_FOUND");
                return;
            }

            StoryChapter.Option selectedOption = currentChapter.getOptions().stream()
                    .filter(o -> o.getId().equals(optionId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(400, "无效的选项"));

            // 保存用户选择
            currentChapter.setSelectedOption(optionId);
            chapterMapper.updateById(currentChapter);

            // 获取关键词和配角
            List<KeywordCard> keywords = getKeywordCardsDetail(userId, story.getKeywordCardIds());
            List<StoryCharacter> characters = characterMapper.selectList(
                    new LambdaQueryWrapper<StoryCharacter>()
                            .eq(StoryCharacter::getStoryId, storyId));

            // 3. 流式生成下一章（AI 协作：判官评估 → 说书人生成）
            int nextChapterNo = chapterNo + 1;
            pushSseEvent(storyId, "chapter_start",
                    "{\"storyId\\\":" + storyId + ",\"chapterNo\":" + nextChapterNo + "}");

            // 流式回调
            java.util.function.Consumer<String> onChunk = chunk -> {
                pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                        .storyId(storyId)
                        .chapterNo(nextChapterNo)
                        .chunk(chunk)
                        .build());
                pushSseChunk(storyId, chunk);
            };

            StoryChapter nextChapter = aiGatewayService.generateNextChapter(
                    story, currentChapter, selectedOption, keywords, characters, onChunk);

            // 4. 保存下一章节
            nextChapter.setStoryId(storyId);
            nextChapter.setSelectedOption(null);
            chapterMapper.insert(nextChapter);

            // 5. 更新故事状态
            story.setCurrentChapter(nextChapterNo);
            story.setHistoryDeviation(Math.max(0, Math.min(100, story.getHistoryDeviation())));
            boolean isLastChapter = nextChapterNo >= maxChapters;
            if (isLastChapter) {
                story.setStatus(2);
            }
            storyMapper.updateById(story);

            // 6. 推送章节结束 + 选项
            pushSseEvent(storyId, "chapter_end",
                    "{\"storyId\\\":" + storyId + ",\"chapterNo\":" + nextChapterNo +
                            ",\"isLastChapter\":" + isLastChapter + "}");

            if (nextChapter.getOptions() != null) {
                pushSseEvent(storyId, "options", toJson(nextChapter.getOptions()));
            }

            // 7. 推送判官判词
            pushToUser(userId, "judgment", JudgmentVO.builder()
                    .storyId(storyId)
                    .chapterNo(nextChapterNo)
                    .judgment(nextChapter.getChapterComment())
                    .deviationChange(0)  // 已由 AI Gateway 更新
                    .build());

            if (isLastChapter) {
                pushSseEvent(storyId, "story_end", "{\"storyId\\\":" + storyId + ",\"status\":\"finished\"}");
                pushToUser(userId, "story_end", StoryEndVO.builder().storyId(storyId).status("finished").build());
            }

            log.info("选择提交并流式生成完成: storyId={}, nextChapterNo={}", storyId, nextChapterNo);

        } catch (Exception e) {
            log.error("submitChoiceAndStream 失败: storyId={}, error={}", storyId, e.getMessage(), e);
            pushSseError(storyId, "处理失败: " + e.getMessage());
        }
    }

    /**
     * ========== REST API 实现 ==========
     */

    /**
     * 开始新故事
     *
     * POST /api/story/start
     */
    @Transactional
    public StartStoryVO startStory(StartStoryRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("开始新故事: userId={}, eventCardId={}, keywordCardIds={}",
                userId, request.getEventCardId(), request.getKeywordCardIds());

        // 1. 验证关键词卡所有权
        validateKeywordCards(userId, request.getKeywordCardIds());

        // 2. 创建故事记录
        Story story = new Story();
        story.setStoryNo(IdGenerator.storyNo());
        story.setUserId(userId);
        story.setEventCardId(request.getEventCardId());
        story.setKeywordCardIds(request.getKeywordCardIds());
        story.setIdentityType(request.getIdentityType());
        story.setTitle(generateStoryTitle(request));
        story.setStyle(request.getStyle() != null ? request.getStyle() : 1);
        story.setHistoryDeviation(50); // 初始偏离度50
        story.setStatus(1); // 进行中
        story.setCurrentChapter(0);
        story.setContextJson("{}");
        story.setCreatedAt(LocalDateTime.now());

        try {
            story.setEntryAnswers(objectMapper.writeValueAsString(request.getEntryAnswers()));
        } catch (Exception e) {
            story.setEntryAnswers("{}");
        }

        storyMapper.insert(story);

        // 3. 初始化配角
        List<StoryCharacter> characters = initializeCharacters(story);
        for (StoryCharacter character : characters) {
            characterMapper.insert(character);
        }

        // 4. 获取关键词卡
        List<KeywordCard> keywords = getKeywordCardsDetail(userId, request.getKeywordCardIds());
        StringBuilder fullText = new StringBuilder();

        // 5. 生成第一章（流式推送）
        StoryChapter firstChapter = aiGatewayService.generateFirstChapter(
                story, keywords, characters,
                chunk -> {
                    // WebSocket 推送
                    pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                            .storyId(story.getId())
                            .chapterNo(1)
                            .chunk(chunk)
                            .build());
                    // SSE 推送
                    pushSseChunk(story.getId(), chunk);
                    fullText.append(chunk);
                }
        );

        // 6. 保存第一章
        firstChapter.setStoryId(story.getId());
        firstChapter.setSelectedOption(null);
        chapterMapper.insert(firstChapter);
        story.setCurrentChapter(1);
        storyMapper.updateById(story);

        // 7. 更新用户统计
        updateUserStoryStats(userId, false);

        log.info("故事创建成功: storyId={}, storyNo={}", story.getId(), story.getStoryNo());

        return StartStoryVO.builder()
                .storyId(story.getId())
                .storyNo(story.getStoryNo())
                .title(story.getTitle())
                .style(story.getStyle())
                .currentChapter(1)
                .chapter(firstChapter)
                .characters(characters)
                .keywords(keywords)
                .build();
    }

    /**
     * 提交选择（轮询模式）
     *
     * POST /api/story/:id/chapter/:no/choose
     */
    @Transactional
    public ChooseResultVO submitChoice(Long storyId, Integer chapterNo, Integer optionId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交选择: userId={}, storyId={}, chapterNo={}, optionId={}",
                userId, storyId, chapterNo, optionId);

        // 1. 验证故事归属
        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }
        if (story.getStatus() != 1) {
            throw new BusinessException(400, "故事已结束，无法继续");
        }

        // 2. 验证章节连续性
        if (!story.getCurrentChapter().equals(chapterNo)) {
            throw new BusinessException(400, "章节序号不连续");
        }

        // 3. 获取当前章节并验证选项
        StoryChapter currentChapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<StoryChapter>()
                        .eq(StoryChapter::getStoryId, storyId)
                        .eq(StoryChapter::getChapterNo, chapterNo));
        if (currentChapter == null) throw BusinessException.CHAPTER_NOT_FOUND;

        StoryChapter.Option selectedOption = currentChapter.getOptions().stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "无效的选项"));

        // 保存用户选择
        currentChapter.setSelectedOption(optionId);
        chapterMapper.updateById(currentChapter);

        // 4. 获取关键词卡和配角
        List<KeywordCard> keywords = getKeywordCardsDetail(userId, story.getKeywordCardIds());
        List<StoryCharacter> characters = characterMapper.selectList(
                new LambdaQueryWrapper<StoryCharacter>()
                        .eq(StoryCharacter::getStoryId, storyId));

        // 5. AI 协作生成下一章（判官评估 → 说书人生成）
        StringBuilder fullText = new StringBuilder();
        StoryChapter nextChapter = aiGatewayService.generateNextChapter(
                story,
                currentChapter,
                selectedOption,
                keywords,
                characters,
                chunk -> {
                    // WebSocket 流式推送
                    pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                            .storyId(storyId)
                            .chapterNo(chapterNo + 1)
                            .chunk(chunk)
                            .build());
                    fullText.append(chunk);
                }
        );

        // 6. 保存下一章节
        nextChapter.setStoryId(storyId);
        nextChapter.setSelectedOption(null);
        chapterMapper.insert(nextChapter);

        // 7. 更新故事状态
        int newChapterNo = chapterNo + 1;
        story.setCurrentChapter(newChapterNo);
        story.setHistoryDeviation(Math.max(0, Math.min(100, story.getHistoryDeviation())));
        boolean isLastChapter = newChapterNo >= maxChapters;
        storyMapper.updateById(story);

        // 8. 推送判官判词（WebSocket）
        pushToUser(userId, "choice_result", ChoiceResultVO.builder()
                .storyId(storyId)
                .deviationChange(0)
                .judgment(nextChapter.getChapterComment())
                .isLastChapter(isLastChapter)
                .build());

        log.info("选择提交成功: storyId={}, nextChapterNo={}, isLastChapter={}",
                storyId, newChapterNo, isLastChapter);

        return ChooseResultVO.builder()
                .storyId(storyId)
                .currentChapter(newChapterNo)
                .chapter(nextChapter)
                .judgment(nextChapter.getChapterComment() != null ?
                        nextChapter.getChapterComment() : "（判官判词将在下一章节显示）")
                .isLastChapter(isLastChapter)
                .build();
    }

    /**
     * 结束故事，生成手稿
     *
     * POST /api/story/:id/finish
     */
    @Transactional
    public FinishStoryVO finishStory(Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("结束故事生成手稿: userId={}, storyId={}", userId, storyId);

        // 1. 验证故事归属
        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }
        if (story.getStatus() != 1) {
            throw BusinessException.STORY_NOT_FINISHED;
        }

        // 2. 获取所有章节
        List<StoryChapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<StoryChapter>()
                        .eq(StoryChapter::getStoryId, storyId)
                        .orderByAsc(StoryChapter::getChapterNo));
        if (chapters.isEmpty()) throw BusinessException.CHAPTER_NOT_FOUND;

        // 3. 获取关键词卡和配角
        List<KeywordCard> keywords = getKeywordCardsDetail(userId, story.getKeywordCardIds());
        List<StoryCharacter> characters = characterMapper.selectList(
                new LambdaQueryWrapper<StoryCharacter>()
                        .eq(StoryCharacter::getStoryId, storyId));

        // 4. 生成手稿正文（说书人）
        String manuscriptText = aiGatewayService.generateManuscript(story, chapters, keywords, characters);

        // 4.1 掌眼 Agent 过滤 AI 腔
        manuscriptText = zhangyanAgent.filter(manuscriptText);
        int wordCount = manuscriptText.length();

        // 5. 生成后日谈（稗官）
        String overallComment = baiguanAgent.generateOverallComment(
                story, wordCount, story.getHistoryDeviation());
        characters = baiguanAgent.generateEpilogues(story, characters, story.getHistoryDeviation());

        // 6. 生成朱批
        List<StoryManuscript.Annotation> annotations = new ArrayList<>();
        for (StoryChapter chapter : chapters) {
            String annotation = baiguanAgent.generateAnnotation(
                    chapter.getChapterNo(), chapter.getSceneText());
            StoryManuscript.Annotation ann = new StoryManuscript.Annotation();
            ann.setChapterNo(chapter.getChapterNo());
            ann.setText(annotation);
            ann.setColor("#C0392B"); // 朱砂红
            annotations.add(ann);
        }

        // 7. 构建选择标记
        List<StoryManuscript.ChoiceMark> choiceMarks = chapters.stream()
                .filter(c -> c.getSelectedOption() != null)
                .map(c -> {
                    StoryManuscript.ChoiceMark mark = new StoryManuscript.ChoiceMark();
                    mark.setChapterNo(c.getChapterNo());
                    mark.setOptionId(c.getSelectedOption());
                    mark.setText(c.getOptions().stream()
                            .filter(o -> o.getId().equals(c.getSelectedOption()))
                            .findFirst()
                            .map(StoryChapter.Option::getText)
                            .orElse(""));
                    return mark;
                })
                .collect(Collectors.toList());

        // 8. 保存手稿
        StoryManuscript manuscript = new StoryManuscript();
        manuscript.setStoryId(storyId);
        manuscript.setFullText(manuscriptText);
        manuscript.setAnnotations(annotations);
        manuscript.setChoiceMarks(choiceMarks);
        manuscript.setEpilogue(overallComment);
        manuscript.setWordCount(wordCount);
        manuscript.setCreatedAt(LocalDateTime.now());
        manuscriptMapper.insert(manuscript);

        // 9. 更新故事状态
        story.setStatus(2); // 已完成
        story.setTotalWords(wordCount);
        story.setFinishedAt(LocalDateTime.now());
        storyMapper.updateById(story);

        // 10. 更新用户统计
        updateUserStoryStats(userId, true);

        log.info("手稿生成完成: storyId={}, wordCount={}", storyId, wordCount);

        return FinishStoryVO.builder()
                .storyId(storyId)
                .manuscript(manuscriptText)
                .wordCount(wordCount)
                .annotations(annotations)
                .choiceMarks(choiceMarks)
                .epilogue(overallComment)
                .historyDeviation(story.getHistoryDeviation())
                .build();
    }

    /**
     * 获取故事详情
     */
    public StoryDetailVO getStoryDetail(Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }

        List<StoryChapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<StoryChapter>()
                        .eq(StoryChapter::getStoryId, storyId)
                        .orderByAsc(StoryChapter::getChapterNo));

        List<StoryCharacter> characters = characterMapper.selectList(
                new LambdaQueryWrapper<StoryCharacter>()
                        .eq(StoryCharacter::getStoryId, storyId));

        StoryManuscript manuscript = null;
        if (story.getStatus() == 2) {
            manuscript = manuscriptMapper.selectById(storyId);
        }

        return StoryDetailVO.builder()
                .storyId(storyId)
                .storyNo(story.getStoryNo())
                .title(story.getTitle())
                .style(story.getStyle())
                .status(story.getStatus())
                .currentChapter(story.getCurrentChapter())
                .historyDeviation(story.getHistoryDeviation())
                .totalWords(story.getTotalWords())
                .createdAt(story.getCreatedAt())
                .finishedAt(story.getFinishedAt())
                .chapters(chapters)
                .characters(characters)
                .manuscript(manuscript)
                .build();
    }

    // ========== 私有辅助方法 ==========

    private void validateKeywordCards(Long userId, List<Long> keywordCardIds) {
        for (Long cardId : keywordCardIds) {
            if (!cardService.hasCard(userId, cardId)) {
                throw new BusinessException(400,
                        "缺少关键词卡: " + cardId + "，请先抽卡或使用已有卡牌");
            }
        }
    }

    private List<StoryCharacter> initializeCharacters(Story story) {
        List<StoryCharacter> characters = new ArrayList<>();

        StoryCharacter c1 = new StoryCharacter();
        c1.setStoryId(story.getId());
        c1.setName("张翁");
        c1.setCharacterType(1);
        c1.setFateValue(50);
        c1.setRelationToUser("故交");
        c1.setCreatedAt(LocalDateTime.now());
        characters.add(c1);

        StoryCharacter c2 = new StoryCharacter();
        c2.setStoryId(story.getId());
        c2.setName("少年");
        c2.setCharacterType(3);
        c2.setFateValue(50);
        c2.setRelationToUser("偶然相识");
        c2.setCreatedAt(LocalDateTime.now());
        characters.add(c2);

        return characters;
    }

    private List<KeywordCard> getKeywordCardsDetail(Long userId, List<Long> keywordCardIds) {
        // TODO: 实际从 CardService 查询
        // 暂时返回空列表，AI 调用时会使用默认行为
        return new ArrayList<>();
    }

    private String getEventCardName(Long eventCardId) {
        // TODO: 实际从 EventCardService 查询
        // 暂时返回默认名称
        Map<Long, String> eventNames = Map.of(
            1L, "巨鹿城·破釜沉舟",
            2L, "赤壁崖·东风骤起",
            3L, "马嵬驿·贵妃缢死",
            4L, "陈桥驿·黄袍加身",
            5L, "崖山海·十万投海",
            6L, "玄武门·李世民射兄"
        );
        return eventNames.getOrDefault(eventCardId, "未知历史事件");
    }

    private String generateStoryTitle(StartStoryRequest request) {
        return "时光旅人手记 · 第" + System.currentTimeMillis() % 10000 + "号";
    }

    private void updateUserStoryStats(Long userId, boolean completed) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setTotalStories(user.getTotalStories() + 1);
            if (completed) {
                user.setCompletedStories(user.getCompletedStories() + 1);
            }
            userMapper.updateById(user);
        }
    }

    private StoryChapter.Option findSelectedOption(StoryChapter chapter) {
        if (chapter.getSelectedOption() == null) return null;
        return chapter.getOptions().stream()
                .filter(o -> o.getId().equals(chapter.getSelectedOption()))
                .findFirst()
                .orElse(null);
    }

    // ========== SSE 推送工具 ==========

    private void pushSseChunk(Long storyId, String chunk) {
        SseEmitter emitter = storySseEmitters.get(storyId);
        if (emitter == null) return;
        try {
        emitter.send(SseEmitter.event()
                    .name("chapter_chunk")
                    .data(chunk)
                    .build());
        } catch (IOException e) {
            log.warn("SSE推送失败: storyId={}, error={}", storyId, e.getMessage());
            storySseEmitters.remove(storyId);
        }
    }

    private void pushSseEvent(Long storyId, String eventName, String data) {
        SseEmitter emitter = storySseEmitters.get(storyId);
        if (emitter == null) return;
        try {
        emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
                    .build());
        } catch (IOException e) {
            log.warn("SSE事件推送失败: storyId={}, event={}", storyId, eventName);
            storySseEmitters.remove(storyId);
        }
    }

    private void pushSseError(Long storyId, String errorMsg) {
        SseEmitter emitter = storySseEmitters.get(storyId);
        if (emitter == null) return;
        try {
        emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"" + errorMsg + "\"}")
                    .build());
        } catch (IOException e) {
            log.warn("SSE错误推送失败: storyId={}", storyId);
        } finally {
            storySseEmitters.remove(storyId);
        }
    }

    // ========== WebSocket 推送工具 ==========

    /**
     * WebSocket 推送（STOMP /user/queue/xxx）
     */
    private void pushToUser(Long userId, String event, Object payload) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            try {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/" + event, payload);
            } catch (Exception e) {
                log.warn("WebSocket推送失败: userId={}, event={}, error={}",
                        userId, event, e.getMessage());
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ========== 入局三问方法 ==========

    /**
     * 生成入局三问（基于关键词组合）
     * POST /api/story/questions
     */
    public QuestionsVO generateEntryQuestions(StoryController.QuestionsRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("生成入局三问: userId={}, keywordIds={}, eventId={}",
                userId, request.getKeywordIds(), request.getEventId());

        // 获取关键词卡详情（用于生成相关问题）
        List<KeywordCard> keywords = getKeywordCardsDetail(userId, request.getKeywordIds());

        // 获取事件卡详情
        String eventName = "未知历史事件";
        if (request.getEventId() != null) {
            eventName = getEventCardName(request.getEventId());
        }

        // 使用 AI 说书人生成3个问题
        List<StoryController.QuestionItem> questions = aiGatewayService.generateEntryQuestions(
                keywords, eventName);

        return QuestionsVO.builder()
                .questions(questions)
                .build();
    }

    /**
     * 提交入局答案并开始故事
     * POST /api/story/answers
     */
    @Transactional
    public StartStoryVO submitEntryAnswers(StoryController.AnswersRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交入局答案并开始故事: userId={}, keywordIds={}, answerCount={}",
                userId, request.getKeywordIds(), request.getEntryAnswers().size());

        // 构建 StartStoryRequest（复用现有逻辑）
        StoryController.StartStoryRequest startRequest = new StoryController.StartStoryRequest();
        startRequest.setEventCardId(request.getEventId());
        startRequest.setKeywordCardIds(request.getKeywordIds());
        startRequest.setIdentityType(1); // 默认高位视角

        // 将入局答案转为 Map 格式
        Map<String, String> entryAnswersMap = new java.util.HashMap<>();
        for (StoryController.EntryAnswerItem item : request.getEntryAnswers()) {
            entryAnswersMap.put("Q" + item.getQuestionId() + ":" + item.getQuestion(),
                    item.getAnswer());
        }
        startRequest.setEntryAnswers(entryAnswersMap);

        // 调用现有的 startStory 逻辑
        return startStory(startRequest);
    }

    // ========== VO 类 ==========

    @Data @lombok.Builder
    public static class StartStoryVO {
        private Long storyId;
        private String storyNo;
        private String title;
        private Integer style;
        private Integer currentChapter;
        private StoryChapter chapter;
        private List<StoryCharacter> characters;
        private List<KeywordCard> keywords;
    }

    @Data @lombok.Builder
    public static class ChooseResultVO {
        private Long storyId;
        private Integer currentChapter;
        private StoryChapter chapter;
        private String judgment;
        private boolean isLastChapter;
    }

    @Data @lombok.Builder
    public static class FinishStoryVO {
        private Long storyId;
        private String manuscript;
        private Integer wordCount;
        private List<StoryManuscript.Annotation> annotations;
        private List<StoryManuscript.ChoiceMark> choiceMarks;
        private String epilogue;
        private Integer historyDeviation;
    }

    @Data @lombok.Builder
    public static class StoryDetailVO {
        private Long storyId;
        private String storyNo;
        private String title;
        private Integer style;
        private Integer status;
        private Integer currentChapter;
        private Integer historyDeviation;
        private Integer totalWords;
        private LocalDateTime createdAt;
        private LocalDateTime finishedAt;
        private List<StoryChapter> chapters;
        private List<StoryCharacter> characters;
        private StoryManuscript manuscript;
    }

    @Data @lombok.Builder
    public static class ChapterStreamVO {
        private Long storyId;
        private Integer chapterNo;
        private String chunk;
    }

    @Data @lombok.Builder
    public static class JudgmentVO {
        private Long storyId;
        private Integer chapterNo;
        private String judgment;
        private Integer deviationChange;
        private Map<String, Integer> characterChanges;
    }

    @Data @lombok.Builder
    public static class ChoiceResultVO {
        private Long storyId;
        private int deviationChange;
        private String judgment;
        private boolean isLastChapter;
    }

    @Data @lombok.Builder
    public static class StoryEndVO {
        private Long storyId;
        private String status;
    }

    @Data @lombok.Builder
    public static class QuestionsVO {
        private List<StoryController.QuestionItem> questions;
    }
}
