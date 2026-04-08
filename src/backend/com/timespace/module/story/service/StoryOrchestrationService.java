package com.timespace.module.story.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.ai.agent.BaiguanAgent;
import com.timespace.module.ai.service.AIGatewayService;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.entity.UserKeywordCard;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
 * 2. 提交选择 (submitChoice)
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
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${timespace.story.max-chapters:5}")
    private int maxChapters;

    @Value("${timespace.story.max-words:8000}")
    private int maxWords;

    // WebSocket会话管理（userId -> WebSocket Session ID）
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

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
     * 开始新故事
     *
     * POST /api/story/start
     *
     * @param request 开始故事请求
     * @return 故事元信息 + 第一章内容
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

        // 保存入局三问答案
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

        // 4. 生成第一章（流式推送）
        List<KeywordCard> keywords = getKeywordCardsDetail(userId, request.getKeywordCardIds());
        StringBuilder fullText = new StringBuilder();

        StoryChapter firstChapter = aiGatewayService.generateFirstChapter(story, chunk -> {
            // 流式推送 WebSocket
            pushToUser(userId, "chapter_stream", ChapterStreamVO.builder()
                    .storyId(story.getId())
                    .chapterNo(1)
                    .chunk(chunk)
                    .build());
            fullText.append(chunk);
        });

        // 5. 保存第一章
        firstChapter.setStoryId(story.getId());
        firstChapter.setSelectedOption(null); // 第一章无选择
        try {
            firstChapter.setOptions(firstChapter.getOptions());
        } catch (Exception e) {
            // ignore
        }
        chapterMapper.insert(firstChapter);
        story.setCurrentChapter(1);

        // 6. 更新故事状态
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
     * 提交选择
     *
     * POST /api/story/:id/chapter/:no/choose
     * 支持 WebSocket 流式推送
     *
     * @param storyId 故事ID
     * @param chapterNo 章节号
     * @param optionId 选择的选项ID
     * @return 下一章节内容
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

        // 5. 触发 AI 协作流程
        StringBuilder fullText = new StringBuilder();
        StoryChapter nextChapter = aiGatewayService.generateNextChapter(
                story,
                currentChapter,
                selectedOption,
                keywords,
                characters,
                chunk -> {
                    // 流式推送
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
        nextChapter.setSelectedOption(null); // 等待用户选择
        chapterMapper.insert(nextChapter);

        // 7. 更新故事状态
        int newChapterNo = chapterNo + 1;
        story.setCurrentChapter(newChapterNo);
        story.setHistoryDeviation(
                Math.max(0, Math.min(100, story.getHistoryDeviation())));

        // 检查是否达到最大章节数
        boolean isLastChapter = newChapterNo >= maxChapters;

        storyMapper.updateById(story);

        log.info("选择提交成功: storyId={}, nextChapterNo={}, isLastChapter={}",
                storyId, newChapterNo, isLastChapter);

        return ChooseResultVO.builder()
                .storyId(storyId)
                .currentChapter(newChapterNo)
                .chapter(nextChapter)
                .judgment("（判官判词将在下一章节显示）")
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
        // 根据事件卡初始化配角（简化实现）
        List<StoryCharacter> characters = new ArrayList<>();

        StoryCharacter c1 = new StoryCharacter();
        c1.setStoryId(story.getId());
        c1.setName("张翁");
        c1.setCharacterType(1); // 命运羁绊
        c1.setFateValue(50);
        c1.setRelationToUser("故交");
        c1.setCreatedAt(LocalDateTime.now());
        characters.add(c1);

        StoryCharacter c2 = new StoryCharacter();
        c2.setStoryId(story.getId());
        c2.setName("少年");
        c2.setCharacterType(3); // 市井过客
        c2.setFateValue(50);
        c2.setRelationToUser("偶然相识");
        c2.setCreatedAt(LocalDateTime.now());
        characters.add(c2);

        return characters;
    }

    private List<KeywordCard> getKeywordCardsDetail(Long userId, List<Long> keywordCardIds) {
        // 实际从数据库查询
        return new ArrayList<>();
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

    /**
     * WebSocket 推送
     */
    private void pushToUser(Long userId, String event, Object payload) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            try {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/" + event, payload);
            } catch (Exception e) {
                log.warn("WebSocket推送失败: userId={}, event={}", userId, event);
            }
        }
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
}
