package com.timespace.module.story.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.ContentSafetyChecker;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.ai.agent.BaiguanAgent;
import com.timespace.module.ai.agent.BaiguanAgent.AnnotationWithType;
import com.timespace.module.ai.agent.EncounterAgent;
import com.timespace.module.ai.agent.EncounterAgent.EncounterResult;
import com.timespace.module.ai.agent.JudgeAgent;
import com.timespace.module.ai.agent.ZhangyanAgent;
import com.timespace.module.ai.service.AIGatewayService;
import com.timespace.module.ai.util.KeywordInsertionChecker;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.service.CardService;
import com.timespace.module.story.controller.StoryController.*;
import com.timespace.module.story.entity.*;
import com.timespace.module.story.mapper.StoryChapterMapper;
import com.timespace.module.story.mapper.StoryCharacterMapper;
import com.timespace.module.story.mapper.StoryEncounterMapper;
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
    private final StoryEncounterMapper encounterMapper;
    private final UserMapper userMapper;
    private final CardService cardService;
    private final AIGatewayService aiGatewayService;
    private final BaiguanAgent baiguanAgent;
    private final ZhangyanAgent zhangyanAgent;
    private final EncounterAgent encounterAgent;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ContentSafetyChecker contentSafetyChecker;

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
                        // 持久化生成进度
                        saveChapterProgress(storyId, chapterNo, chunk);
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
                currentChapter.setGeneratedLength(fullText != null ? fullText.length() : 0);
                // options 已在 AI 调用中设置
                chapterMapper.insert(currentChapter);
            } else {
                // 已有预插入记录，更新最终内容
                currentChapter.setSceneText(fullText);
                currentChapter.setGeneratedLength(fullText != null ? fullText.length() : 0);
                chapterMapper.updateById(currentChapter);
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

            // 流式回调：更新数据库进度
            java.util.function.Consumer<String> onChunk = chunk -> {
                pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                        .storyId(storyId)
                        .chapterNo(nextChapterNo)
                        .chunk(chunk)
                        .build());
                pushSseChunk(storyId, chunk);
                // 持久化生成进度
                saveChapterProgress(storyId, nextChapterNo, chunk);
            };

            StoryChapter nextChapter = aiGatewayService.generateNextChapter(
                    story, currentChapter, selectedOption, keywords, characters, onChunk);

            // 4. 保存下一章节
            nextChapter.setStoryId(storyId);
            nextChapter.setSelectedOption(null);
            nextChapter.setGeneratedLength(nextChapter.getSceneText() != null ? nextChapter.getSceneText().length() : 0);
            chapterMapper.insert(nextChapter);

            // 4.1 更新配角的初见印象
            if (nextChapter.getCharacterAppearances() != null && !nextChapter.getCharacterAppearances().isEmpty()) {
                for (StoryChapter.CharacterAppearance app : nextChapter.getCharacterAppearances()) {
                    characterMapper.selectList(
                            new LambdaQueryWrapper<StoryCharacter>()
                                    .eq(StoryCharacter::getStoryId, storyId)
                                    .eq(StoryCharacter::getName, app.getName()))
                            .stream().findFirst()
                            .ifPresent(c -> {
                                if (c.getFirstImpression() == null || c.getFirstImpression().isEmpty()) {
                                    c.setFirstImpression(app.getFirstImpression());
                                    characterMapper.updateById(c);
                                }
                            });
                }
            }

            // 5. 更新故事状态
            story.setCurrentChapter(nextChapterNo);
            story.setHistoryDeviation(Math.max(0, Math.min(100, story.getHistoryDeviation())));
            boolean isLastChapter = nextChapterNo >= maxChapters;
            if (isLastChapter) {
                story.setStatus(2);
            }
            storyMapper.updateById(story);

            // 5.1 S-14: 章节完成后，以30%概率触发偶遇事件
            EncounterVO encounter = triggerEncounterIfNeeded(storyId, nextChapterNo, characters);
            if (encounter != null) {
                // 推送偶遇事件（半屏浮层），前端应先显示偶遇，用户选择后再显示选项
                pushSseEvent(storyId, "encounter",
                        "{\"encounterId\":" + encounter.getEncounterId() +
                        ",\"encounterText\":\"" + escapeJson(encounter.getEncounterText()) + "\"" +
                        ",\"optionA\":\"" + escapeJson(encounter.getOptionA()) + "\"" +
                        ",\"optionB\":\"" + escapeJson(encounter.getOptionB()) + "\"" +
                        ",\"chapterNo\":" + encounter.getChapterNo() + "}");
            }

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
        // 预先插入章节记录（进度追踪）
        StoryChapter preChapter = new StoryChapter();
        preChapter.setStoryId(story.getId());
        preChapter.setChapterNo(1);
        preChapter.setGeneratedLength(0);
        chapterMapper.insert(preChapter);

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
                    // 持久化生成进度
                    saveChapterProgress(story.getId(), 1, chunk);
                }
        );

        // 更新第一章（覆盖预插入记录）
        firstChapter.setStoryId(story.getId());
        firstChapter.setSelectedOption(null);
        firstChapter.setGeneratedLength(fullText.length());
        chapterMapper.updateById(firstChapter);

        // 6. 保存第一章
        firstChapter.setStoryId(story.getId());
        firstChapter.setSelectedOption(null);
        chapterMapper.insert(firstChapter);
        story.setCurrentChapter(1);
        storyMapper.updateById(story);

        // 7. 更新用户统计
        updateUserStoryStats(userId, false);

        log.info("故事创建成功: storyId={}, storyNo={}", story.getId(), story.getStoryNo());

        // E-07: 提取关键词落位（仅第一章有值）
        List<StoryChapter.KeywordPosition> keywordPositions = null;
        if (firstChapter != null && firstChapter.getKeywordPositions() != null && !firstChapter.getKeywordPositions().isEmpty()) {
            keywordPositions = firstChapter.getKeywordPositions();
            log.info("关键词落位已生成: {}", keywordPositions);
        }

        return StartStoryVO.builder()
                .storyId(story.getId())
                .storyNo(story.getStoryNo())
                .title(story.getTitle())
                .style(story.getStyle())
                .currentChapter(1)
                .chapter(firstChapter)
                .characters(characters)
                .keywords(keywords)
                .keywordPositions(keywordPositions)
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

        // 6.1 更新配角的初见印象（如果AI返回了新角色的初见描写）
        if (nextChapter.getCharacterAppearances() != null && !nextChapter.getCharacterAppearances().isEmpty()) {
            for (StoryChapter.CharacterAppearance app : nextChapter.getCharacterAppearances()) {
                characterMapper.selectList(
                        new LambdaQueryWrapper<StoryCharacter>()
                                .eq(StoryCharacter::getStoryId, storyId)
                                .eq(StoryCharacter::getName, app.getName()))
                        .stream().findFirst()
                        .ifPresent(c -> {
                            if (c.getFirstImpression() == null || c.getFirstImpression().isEmpty()) {
                                c.setFirstImpression(app.getFirstImpression());
                                characterMapper.updateById(c);
                            }
                        });
            }
        }

        // 6.2 S-14: 章节完成后，以30%概率触发偶遇事件
        EncounterVO encounter = triggerEncounterIfNeeded(storyId, newChapterNo, characters);

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
                .encounter(encounter)
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

        // 4. 生成手稿正文（说书人，含3个备选标题和题记）
        AIGatewayService.ManuscriptResult manuscriptResult =
                aiGatewayService.generateManuscriptWithTitles(story, chapters, keywords, characters);
        String manuscriptText = manuscriptResult.manuscriptText();

        // 4.1 掌眼 Agent 过滤 AI 腔
        manuscriptText = zhangyanAgent.filter(manuscriptText);

        // 4.2 AI-08: 内容安全检测（手稿正文）
        // AIGatewayService.generateManuscriptWithTitles 已做过一次安全检测，
        // 但 ZhangyanAgent 过滤可能改变文本内容，在此二次确认
        ContentSafetyChecker.SafetyResult manuscriptSafety =
                contentSafetyChecker.check(manuscriptText);
        if (!manuscriptSafety.isSafe()) {
            log.warn("[AI-08] 手稿正文内容安全检测不通过: {}, 尝试重新生成...",
                    manuscriptSafety.getReason());
            // 重新生成手稿（带关键词强调 prompt）
            KeywordInsertionChecker.CheckResult kwResult =
                    KeywordInsertionChecker.check(manuscriptText, keywords);
            String emphasisPrompt = kwResult.isSufficient() ? "" : kwResult.getWarning();
            AIGatewayService.ManuscriptResult retryResult =
                    aiGatewayService.generateManuscriptWithTitles(
                            story, chapters, keywords, characters, emphasisPrompt);
            manuscriptText = zhangyanAgent.filter(retryResult.getManuscriptText());
            // 二次安全检测
            manuscriptSafety = contentSafetyChecker.check(manuscriptText);
            if (!manuscriptSafety.isSafe()) {
                log.error("[AI-08] 手稿正文内容安全二次检测仍不通过，使用兜底文案");
                manuscriptText = "（此篇故事，因时局所限，暂时无法完整呈现）";
            }
        }

        // 4.3 AI-09: 关键词融入率检测（≥3 个关键词）
        KeywordInsertionChecker.CheckResult kwCheck =
                KeywordInsertionChecker.check(manuscriptText, keywords);

        int wordCount = manuscriptText.length();

        // 4.4 题记（已在 AIGatewayService 中掌眼过滤）
        String inscription = manuscriptResult.inscription();

        // 4.5 备选标题
        List<String> candidateTitles = manuscriptResult.candidateTitles();

        // 5. 生成后日谈（稗官）+ 内容安全检测
        String overallComment = baiguanAgent.generateOverallComment(
                story, wordCount, story.getHistoryDeviation());
        ContentSafetyChecker.SafetyResult commentResult =
                contentSafetyChecker.checkWithRetry(overallComment, 3, null, null);
        if (!commentResult.isSafe()) {
            log.warn("[ContentSafety] 稗官总评不通过: {}, 使用兜底文案", commentResult.getReason());
            overallComment = "（此段故事，因时局所限，未能详述）";
        }

        characters = baiguanAgent.generateEpilogues(story, characters, story.getHistoryDeviation());
        // 对每条后日谈做安全检测
        for (StoryCharacter character : characters) {
            if (character.getFinalEpilogue() != null) {
                ContentSafetyChecker.SafetyResult epResult =
                        contentSafetyChecker.checkWithRetry(character.getFinalEpilogue(), 3, null, null);
                if (!epResult.isSafe()) {
                    log.warn("[ContentSafety] 配角{}后日谈不通过: {}", character.getName(), epResult.getReason());
                    character.setFinalEpilogue("（此人身世，待后人考证）");
                }
            }
        }

        // 6. 生成朱批 + 内容安全检测
        // M-10: 批注支持 normal/easter_egg 两种类型，前端黛青色区分彩蛋批注
        List<StoryManuscript.Annotation> annotations = new ArrayList<>();
        for (StoryChapter chapter : chapters) {
            String annotationRaw = baiguanAgent.generateAnnotation(
                    chapter.getChapterNo(), chapter.getSceneText());
            ContentSafetyChecker.SafetyResult annResult =
                    contentSafetyChecker.checkWithRetry(annotationRaw, 3, null, null);
            if (!annResult.isSafe()) {
                log.warn("[ContentSafety] 第{}章朱批不通过: {}", chapter.getChapterNo(), annResult.getReason());
                annotationRaw = "[{\"text\":\"（此处原文晦涩，暂不批注）\",\"type\":\"normal\"}]";
            }
            // M-10: 解析带类型的批注列表
            List<AnnotationWithType> parsed = baiguanAgent.parseAnnotationResponse(annotationRaw);
            for (AnnotationWithType item : parsed) {
                StoryManuscript.Annotation ann = new StoryManuscript.Annotation();
                ann.setChapterNo(chapter.getChapterNo());
                ann.setText(item.getText());
                // M-10: easter_egg 批注标记 type 字段，前端用黛青色 (#4A6B6B) 渲染
                ann.setType(item.getType());
                // 普通批注用赭石色，彩蛋批注由前端根据 type 渲染为黛青色
                ann.setColor("normal".equals(item.getType()) ? "#8B5E3C" : "#4A6B6B");
                annotations.add(ann);
            }
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
        manuscript.setInscription(inscription);
        manuscript.setWordCount(wordCount);
        manuscript.setCreatedAt(LocalDateTime.now());
        manuscriptMapper.insert(manuscript);

        // 9. 更新故事状态和备选标题
        story.setStatus(2); // 已完成
        story.setTotalWords(wordCount);
        story.setFinishedAt(LocalDateTime.now());
        try {
            story.setCandidateTitles(objectMapper.writeValueAsString(candidateTitles));
        } catch (Exception e) {
            log.warn("备选标题序列化失败: {}", e.getMessage());
            story.setCandidateTitles("[\"时光旅人手记\",\"旧事新说\",\"一段往事\"]");
        }
        storyMapper.updateById(story);

        // 10. 更新用户统计
        updateUserStoryStats(userId, true);

        log.info("手稿生成完成: storyId={}, wordCount={}, candidateTitles={}",
                storyId, wordCount, candidateTitles);

        return FinishStoryVO.builder()
                .storyId(storyId)
                .manuscript(manuscriptText)
                .wordCount(wordCount)
                .annotations(annotations)
                .choiceMarks(choiceMarks)
                .epilogue(overallComment)
                .inscription(inscription)
                .historyDeviation(story.getHistoryDeviation())
                .candidateTitles(candidateTitles)
                .keywordWarning(kwCheck.isSufficient() ? null : kwCheck.getWarning())
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
                .candidateTitles(story.getCandidateTitles())
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

    /**
     * 更新故事标题
     *
     * POST /api/story/{id}/title
     */
    public void updateStoryTitle(Long storyId, String title) {
        long userId = StpUtil.getLoginIdAsLong();
        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        story.setTitle(title);
        storyMapper.updateById(story);
        log.info("故事标题更新: storyId={}, title={}", storyId, title);
    }

    /**
     * 获取章节生成进度（用于断线重连）
     *
     * GET /api/story/{id}/chapter/{no}/progress
     */
    public ChapterProgressVO getChapterProgress(Long storyId, Integer chapterNo) {
        long userId = StpUtil.getLoginIdAsLong();
        Story story = storyMapper.selectById(storyId);
        if (story == null || !story.getUserId().equals(userId)) {
            throw BusinessException.STORY_NOT_FOUND;
        }

        StoryChapter chapter = chapterMapper.selectOne(
                new LambdaQueryWrapper<StoryChapter>()
                        .eq(StoryChapter::getStoryId, storyId)
                        .eq(StoryChapter::getChapterNo, chapterNo));

        int generatedLength = 0;
        if (chapter != null && chapter.getSceneText() != null) {
            generatedLength = chapter.getSceneText().length();
        }

        return ChapterProgressVO.builder()
                .chapterNo(chapterNo)
                .generatedLength(generatedLength)
                .build();
    }

    // ========== S-14 偶遇支线 ========== //

    /**
     * S-14: 章节完成后，以30%概率触发偶遇事件
     *
     * @return EncounterVO 如果触发了偶遇；null 如果未触发
     */
    private EncounterVO triggerEncounterIfNeeded(Long storyId, int currentChapterNo,
                                               List<StoryCharacter> characters) {
        // 30% 概率触发
        if (Math.random() > 0.30) {
            return null;
        }

        Story story = storyMapper.selectById(storyId);
        if (story == null) return null;

        try {
            // 调用偶遇 Agent 生成偶遇场景
            EncounterResult result = encounterAgent.generateEncounter(story, currentChapterNo, characters);
            if (result == null || result.getEncounterText() == null) return null;

            // 写入数据库
            StoryEncounter encounter = new StoryEncounter();
            encounter.setStoryId(storyId);
            encounter.setChapterNo(currentChapterNo);
            encounter.setEncounterText(result.getEncounterText());
            encounter.setOptionA(result.getOptionA());
            encounter.setOptionB(result.getOptionB());
            encounter.setFateChange(0); // 等待用户选择后再更新
            encounterMapper.insert(encounter);

            log.info("[S-14] 偶遇触发: storyId={}, chapterNo={}, encounterId={}",
                    storyId, currentChapterNo, encounter.getId());

            return EncounterVO.builder()
                    .encounterId(encounter.getId())
                    .encounterText(result.getEncounterText())
                    .optionA(result.getOptionA())
                    .optionB(result.getOptionB())
                    .chapterNo(currentChapterNo)
                    .build();
        } catch (Exception e) {
            log.warn("[S-14] 偶遇生成异常: storyId={}, error={}", storyId, e.getMessage());
            return null;
        }
    }

    /**
     * S-14: 提交偶遇选择（搭话/装没看见），影响命运值
     *
     * @param storyId 故事ID
     * @param encounterId 偶遇记录ID
     * @param choice 'A' 或 'B'
     * @return 更新后的命运值变化
     */
    @Transactional
    public EncounterChoiceResultVO submitEncounterChoice(Long storyId, Long encounterId, String choice) {
        long userId = StpUtil.getLoginIdAsLong();

        StoryEncounter encounter = encounterMapper.selectById(encounterId);
        if (encounter == null || !encounter.getStoryId().equals(storyId)) {
            throw new BusinessException(404, "偶遇记录不存在");
        }
        if (encounter.getChoiceResult() != null) {
            throw new BusinessException(400, "该偶遇已做出选择");
        }

        // 解析选择
        boolean isChoiceA = "A".equalsIgnoreCase(choice);
        int fateChange = isChoiceA ? 10 : -5;

        // 更新偶遇记录
        encounter.setChoiceResult(isChoiceA ? "A" : "B");
        encounter.setFateChange(fateChange);
        encounterMapper.updateById(encounter);

        // 更新主角命运值（故事表）
        Story story = storyMapper.selectById(storyId);
        if (story != null) {
            int newFate = Math.max(0, Math.min(100, (story.getHistoryDeviation() != null ? story.getHistoryDeviation() : 50) + fateChange));
            story.setHistoryDeviation(newFate);
            storyMapper.updateById(story);
        }

        log.info("[S-14] 偶遇选择: storyId={}, encounterId={}, choice={}, fateChange={}",
                storyId, encounterId, choice, fateChange);

        return EncounterChoiceResultVO.builder()
                .encounterId(encounterId)
                .fateChange(fateChange)
                .build();
    }

    /** S-14 偶遇选择结果 */
    @Data @lombok.Builder
    public static class EncounterChoiceResultVO {
        private Long encounterId;
        private Integer fateChange;
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

    // ========== 断线重连进度追踪 ==========

    /**
     * 增量保存章节生成进度（append 到 sceneText）
     * 用于 WebSocket 断线重连后前端补全内容
     */
    private void saveChapterProgress(Long storyId, Integer chapterNo, String chunk) {
        try {
            StoryChapter chapter = chapterMapper.selectOne(
                    new LambdaQueryWrapper<StoryChapter>()
                            .eq(StoryChapter::getStoryId, storyId)
                            .eq(StoryChapter::getChapterNo, chapterNo));
            if (chapter != null) {
                String existingText = chapter.getSceneText() != null ? chapter.getSceneText() : "";
                chapter.setSceneText(existingText + chunk);
                chapter.setGeneratedLength(chapter.getSceneText().length());
                chapterMapper.updateById(chapter);
            }
        } catch (Exception e) {
            log.warn("saveChapterProgress failed: storyId={}, chapterNo={}, error={}",
                    storyId, chapterNo, e.getMessage());
        }
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

    /**
     * 简单JSON字符串转义（用于SSE事件中的字符串字段）
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
        startRequest.setStyle(request.getStyle() != null ? request.getStyle() : 1);

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
        /** E-07 关键词落位：三个关键词的角色归属 */
        private List<StoryChapter.KeywordPosition> keywordPositions;
    }

    @Data @lombok.Builder
    public static class ChooseResultVO {
        private Long storyId;
        private Integer currentChapter;
        private StoryChapter chapter;
        private String judgment;
        private boolean isLastChapter;
        /** S-14: 偶遇事件（如果触发了的话） */
        private EncounterVO encounter;
    }

    /** S-14 偶遇事件 VO */
    @Data @lombok.Builder
    public static class EncounterVO {
        private Long encounterId;      // story_encounter.id
        private String encounterText; // 偶遇场景描述
        private String optionA;       // 搭话
        private String optionB;       // 装作没看见
        private Integer chapterNo;     // 触发章节号（选择后将进入 chapterNo+1）
    }

    @Data @lombok.Builder
    public static class FinishStoryVO {
        private Long storyId;
        private String manuscript;
        private Integer wordCount;
        private List<StoryManuscript.Annotation> annotations;
        private List<StoryManuscript.ChoiceMark> choiceMarks;
        private String epilogue;
        private String inscription;
        private Integer historyDeviation;
        private List<String> candidateTitles;
        /** AI-09: 关键词融入率警告（少于3个关键词时非空） */
        private String keywordWarning;
    }

    @Data @lombok.Builder
    public static class StoryDetailVO {
        private Long storyId;
        private String storyNo;
        private String title;
        private List<String> candidateTitles;
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
