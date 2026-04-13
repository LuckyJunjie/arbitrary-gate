package com.timespace.module.story.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.card.service.CardService;
import com.timespace.module.card.entity.EventCard;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.card.mapper.EventCardMapper;
import com.timespace.module.card.mapper.KeywordCardMapper;
import com.timespace.module.ai.agent.JudgeAgent;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.service.StoryOrchestrationService;
import com.timespace.module.story.service.StoryOrchestrationService.StartStoryVO;
import com.timespace.module.story.service.StoryOrchestrationService.ChooseResultVO;
import com.timespace.module.story.service.StoryOrchestrationService.EncounterChoiceResultVO;
import com.timespace.module.story.service.StoryOrchestrationService.EncounterVO;
import com.timespace.module.story.service.StoryOrchestrationService.FinishStoryVO;
import com.timespace.module.story.service.StoryOrchestrationService.StoryDetailVO;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/story")
@RequiredArgsConstructor
public class StoryController {

    private final StoryOrchestrationService storyService;
    private final CardService cardService;
    private final JudgeAgent judgeAgent;
    private final KeywordCardMapper keywordCardMapper;
    private final EventCardMapper eventCardMapper;

    // ========== SSE 流式端点 ==========

    /**
     * GET /api/story/{id}/stream
     * SSE 流式推送故事内容
     *
     * 前端连接方式（EventSource）：
     *   const es = new EventSource('/api/story/123/stream');
     *   es.addEventListener('chapter_start', e => { ... });
     *   es.addEventListener('chapter_chunk', e => { appendText(e.data); });
     *   es.addEventListener('chapter_end', e => { ... });
     *   es.addEventListener('options', e => { showOptions(JSON.parse(e.data)); });
     *   es.addEventListener('error', e => { handleError(JSON.parse(e.data)); });
     *
     * 认证：通过 Cookie 或 header 携带 Sa-Token token
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter storyStream(@PathVariable("id") Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("SSE流式请求: userId={}, storyId={}", userId, storyId);
        return storyService.storyStream(storyId);
    }

    /**
     * POST /api/story/{id}/stream/generate
     * 触发 SSE 章节流式生成（通常由前端调用，或由前端轮询检测到未完成章节后触发）
     *
     * 请求：
     * {
     *   "chapterNo": 2    // 可选，默认当前章节+1
     * }
     */
    @PostMapping("/{id}/stream/generate")
    public Result<Void> triggerStreamGenerate(
            @PathVariable("id") Long storyId,
            @RequestBody(required = false) StreamGenerateRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("触发SSE流式生成: userId={}, storyId={}, chapterNo={}",
                userId, storyId, request != null ? request.getChapterNo() : "default");

        // 异步执行（不阻塞 HTTP 响应）
        int chapterNo = request != null && request.getChapterNo() != null
                ? request.getChapterNo()
                : 1; // 由 service 层判断下一章

        storyService.generateChapterStream(storyId, chapterNo);
        return Result.ok(null);
    }

    // ========== 入局三问端点 ==========

    /**
     * POST /api/story/questions
     * 生成入局三问（基于关键词组合）
     *
     * 请求：
     * {
     *   "keywordIds": [1, 2, 3],
     *   "eventId": 101
     * }
     *
     * 响应：
     * {
     *   "questions": [
     *     { "id": 1, "category": "角色背景", "question": "...", "hint": "..." },
     *     { "id": 2, "category": "当下处境", "question": "...", "hint": "..." },
     *     { "id": 3, "category": "内心渴望", "question": "...", "hint": "..." }
     *   ]
     * }
     */
    @PostMapping("/questions")
    public Result<QuestionsVO> generateQuestions(@RequestBody QuestionsRequest request) {
        log.info("生成入局三问: keywordIds={}, eventId={}", request.getKeywordIds(), request.getEventId());
        QuestionsVO vo = storyService.generateEntryQuestions(request);
        return Result.ok(vo);
    }

    /**
     * POST /api/story/answers
     * 提交入局答案并开始故事
     *
     * 请求：
     * {
     *   "keywordIds": [1, 2, 3],
     *   "eventId": 101,
     *   "entryAnswers": [
     *     { "questionId": 1, "question": "...", "answer": "..." },
     *     { "questionId": 2, "question": "...", "answer": "..." },
     *     { "questionId": 3, "question": "...", "answer": "..." }
     *   ]
     * }
     */
    @PostMapping("/answers")
    public Result<StartStoryVO> submitAnswers(@RequestBody AnswersRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交入局答案: userId={}, keywordIds={}, answerCount={}",
                userId, request.getKeywordIds(), request.getEntryAnswers().size());
        StartStoryVO vo = storyService.submitEntryAnswers(request);
        return Result.ok(vo);
    }

    // ========== REST 端点 ==========

    /**
     * POST /api/story/start
     * 开始新故事
     */
    @PostMapping("/start")
    public Result<StartStoryVO> startStory(@Valid @RequestBody StartStoryRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("开始故事请求: userId={}", userId);
        StartStoryVO vo = storyService.startStory(request);
        return Result.ok(vo);
    }

    /**
     * POST /api/story/{id}/chapter/{no}/choose
     * 提交选择（支持 WebSocket 流式 + SSE 流式）
     *
     * 请求：
     * {
     *   "optionId": 1
     * }
     *
     * 响应（轮询模式）：
     * {
     *   "code": 200,
     *   "data": {
     *     "storyId": 1,
     *     "currentChapter": 2,
     *     "chapter": { ... },
     *     "judgment": "此选择虽保全了XX，却牺牲了XX...",
     *     "isLastChapter": false
     *   }
     * }
     *
     * 【WebSocket 流式推送说明】
     * 连接: /ws/story/{storyId}
     * 认证: 通过 STOMP 帧携带 token
     *
     * 推送事件:
     * - /user/queue/chapter_stream : 章节内容流式片段
     * - /user/queue/choice_result  : 选择评估结果
     * - /user/queue/judgment       : 判官判词
     * - /user/queue/story_end       : 故事完成
     */
    @PostMapping("/{id}/chapter/{no}/choose")
    public Result<ChooseResultVO> submitChoice(
            @PathVariable("id") Long storyId,
            @PathVariable("no") Integer chapterNo,
            @Valid @RequestBody ChooseRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交选择请求: userId={}, storyId={}, chapterNo={}, optionId={}, gestureIntensity={}",
                userId, storyId, chapterNo, request.getOptionId(), request.getGestureIntensity());
        ChooseResultVO vo = storyService.submitChoice(storyId, chapterNo, request.getOptionId(), request.getGestureIntensity());
        return Result.ok(vo);
    }

    /**
     * POST /api/story/{id}/chapter/{no}/choose-and-stream
     * 提交选择并触发 SSE 流式生成下一章
     *
     * 与 submitChoice 的区别：
     * - submitChoice：同步返回下一章（轮询模式）
     * - submitChoiceAndStream：异步 SSE 流式推送，HTTP 立即返回
     */
    @PostMapping("/{id}/chapter/{no}/choose-and-stream")
    public Result<Void> submitChoiceAndStream(
            @PathVariable("id") Long storyId,
            @PathVariable("no") Integer chapterNo,
            @Valid @RequestBody ChooseRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交选择并流式生成: userId={}, storyId={}, chapterNo={}, optionId={}, gestureIntensity={}",
                userId, storyId, chapterNo, request.getOptionId(), request.getGestureIntensity());

        // 异步执行，通过 SSE 推送结果
        storyService.submitChoiceAndStream(storyId, chapterNo, request.getOptionId(), request.getGestureIntensity());
        return Result.ok(null);
    }

    /**
     * POST /api/story/{id}/finish
     * 结束故事，生成手稿
     */
    @PostMapping("/{id}/finish")
    public Result<FinishStoryVO> finishStory(@PathVariable("id") Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("结束故事请求: userId={}, storyId={}", userId, storyId);
        FinishStoryVO vo = storyService.finishStory(storyId);
        return Result.ok(vo);
    }

    /**
     * GET /api/story/{id}
     * 获取故事详情
     */
    @GetMapping("/{id}")
    public Result<StoryDetailVO> getStoryDetail(@PathVariable("id") Long storyId) {
        StoryDetailVO vo = storyService.getStoryDetail(storyId);
        return Result.ok(vo);
    }

    /**
     * POST /api/story/{id}/title
     * 选择/更新故事标题
     *
     * 请求：
     * {
     *   "title": "用户选择的标题"
     * }
     */
    @PostMapping("/{id}/title")
    public Result<Void> updateStoryTitle(
            @PathVariable("id") Long storyId,
            @RequestBody UpdateTitleRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("更新故事标题: userId={}, storyId={}, title={}", userId, storyId, request.getTitle());
        storyService.updateStoryTitle(storyId, request.getTitle());
        return Result.ok(null);
    }

    /**
     * GET /api/story/{id}/chapter/{no}/progress
     * 返回当前章节已生成的文本长度（用于断线重连）
     *
     * 响应：
     * {
     *   "chapterNo": 1,
     *   "generatedLength": 256
     * }
     */
    @GetMapping("/{id}/chapter/{no}/progress")
    public Result<ChapterProgressVO> getChapterProgress(
            @PathVariable("id") Long storyId,
            @PathVariable("no") Integer chapterNo) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("章节进度查询: userId={}, storyId={}, chapterNo={}", userId, storyId, chapterNo);
        ChapterProgressVO vo = storyService.getChapterProgress(storyId, chapterNo);
        return Result.ok(vo);
    }

    /**
     * GET /api/story/list
     * 获取用户的故事列表（分页）
     *
     * @param page    页码（默认1）
     * @param pageSize 每页数量（默认10，最大50）
     * @return 分页结果 { list: [...], total: N, page: X, pageSize: Y }
     */
    @GetMapping("/list")
    public Result<PageResponse<StoryListItemVO>> getStoryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();

        // 参数校验与限制
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);

        // 调用服务层分页查询
        IPage<Story> storyPage = storyService.getStoryPage(userId, safePage, safePageSize);

        // 转换为 VO
        List<StoryListItemVO> voList = storyPage.getRecords().stream()
                .map(this::convertToStoryListItemVO)
                .collect(Collectors.toList());

        // 构建分页响应
        PageResponse<StoryListItemVO> response = new PageResponse<>(
                voList,
                storyPage.getTotal(),
                safePage,
                safePageSize
        );

        return Result.ok(response);
    }

    /**
     * 将 Story 实体转换为 StoryListItemVO
     */
    private StoryListItemVO convertToStoryListItemVO(Story story) {
        return StoryListItemVO.builder()
                .storyId(story.getId())
                .storyNo(story.getStoryNo())
                .title(story.getTitle())
                .style(story.getStyle())
                .status(story.getStatus())
                .currentChapter(story.getCurrentChapter())
                .totalWords(story.getTotalWords())
                .historyDeviation(story.getHistoryDeviation())
                .createdAt(story.getCreatedAt() != null ? story.getCreatedAt().toString() : null)
                .finishedAt(story.getFinishedAt() != null ? story.getFinishedAt().toString() : null)
                .build();
    }

    /**
     * 分页响应封装
     */
    @Data
    public static class PageResponse<T> {
        private List<T> list;
        private long total;
        private int page;
        private int pageSize;

        public PageResponse(List<T> list, long total, int page, int pageSize) {
            this.list = list;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
        }
    }

    // ========== S-14 偶遇支线 ========== //

    /**
     * POST /api/story/encounter/trigger
     * 手动触发偶遇（绕过30%概率），用于测试或特定剧情节点
     *
     * 请求：
     * {
     *   "storyId": 123,
     *   "chapterNo": 2
     * }
     */
    @PostMapping("/encounter/trigger")
    public Result<EncounterVO> triggerEncounter(@RequestBody EncounterTriggerRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("[S-14] 手动触发偶遇请求: userId={}, storyId={}, chapterNo={}",
                userId, request.getStoryId(), request.getChapterNo());
        EncounterVO vo = storyService.maybeTriggerEncounter(request.getStoryId(), request.getChapterNo());
        return Result.ok(vo);
    }

    /**
     * POST /api/story/{id}/encounter/choice
     * 提交偶遇选择（搭话A / 装没看见B），影响命运值
     *
     * 请求：
     * {
     *   "encounterId": 123,
     *   "choice": "A"   // A=搭话(+10), B=装没看见(-5)
     * }
     */
    @PostMapping("/{id}/encounter/choice")
    public Result<EncounterChoiceResultVO> submitEncounterChoice(
            @PathVariable("id") Long storyId,
            @RequestBody EncounterChoiceRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("[S-14] 偶遇选择请求: userId={}, storyId={}, encounterId={}, choice={}",
                userId, storyId, request.getEncounterId(), request.getChoice());
        EncounterChoiceResultVO vo = storyService.submitEncounterChoice(
                storyId, request.getEncounterId(), request.getChoice());
        return Result.ok(vo);
    }

    // ========== 请求/响应 DTO ==========

    @Data
    public static class StartStoryRequest {
        private Long eventCardId;
        private List<Long> keywordCardIds; // 2-5张
        private Integer identityType;      // 1高位 2低位 3旁观者
        private Integer style;             // 1白描 2江湖 3笔记 4话本
        private Map<String, String> entryAnswers; // 入局三问答案
    }

    @Data
    public static class ChooseRequest {
        private Integer optionId;
        /** S-11 手势轻重缓急: gentle=轻柔, urgent=急促, forceful=用力 */
        private String gestureIntensity;
    }

    @Data
    public static class EncounterChoiceRequest {
        private Long encounterId;
        /** 'A' = 搭话(+10命运值), 'B' = 装作没看见(-5命运值) */
        private String choice;
    }

    @Data
    public static class EncounterTriggerRequest {
        private Long storyId;
        private Integer chapterNo;
    }

    @Data
    public static class StreamGenerateRequest {
        private Integer chapterNo;
    }

    @Data
    @lombok.Builder
    public static class StoryListItemVO {
        private Long storyId;
        private String storyNo;
        private String title;
        private Integer style;
        private Integer status;
        private Integer currentChapter;
        private Integer totalWords;
        private Integer historyDeviation;
        private String createdAt;
        private String finishedAt;
    }

    // ========== 入局三问 DTO ==========

    @Data
    public static class QuestionsRequest {
        private List<Long> keywordIds;
        private Long eventId;
    }

    @Data
    public static class QuestionsVO {
        private List<QuestionItem> questions;
    }

    @Data
    @lombok.Builder
    public static class QuestionItem {
        private Long id;
        private String category;
        private String question;
        private String hint;
    }

    @Data
    public static class AnswersRequest {
        private List<Long> keywordIds;
        private Long eventId;
        private List<EntryAnswerItem> entryAnswers;
        private Integer style; // 1白描 2江湖 3笔记 4话本
    }

    @Data
    public static class EntryAnswerItem {
        private Long questionId;
        private String question;
        private String answer;
    }

    @Data
    public static class UpdateTitleRequest {
        private String title;
    }

    @Data
    public static class ChapterProgressVO {
        private Integer chapterNo;
        private Integer generatedLength;
    }

    // ========== P-01 组合判词预览 ==========

    /**
     * POST /api/story/preview-judgment
     * P-01: 当用户选定 3 个关键词 + 1 个事件后，生成一句极简判词暗示故事走向
     *
     * 请求：
     * {
     *   "keywordIds": [1, 2, 3],
     *   "eventId": 101
     * }
     *
     * 响应：
     * {
     *   "judgment": "风云将起，命悬一线。"
     * }
     */
    @PostMapping("/preview-judgment")
    public Result<PreviewJudgmentVO> previewJudgment(@RequestBody PreviewJudgmentRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("[P-01] 组合判词预览请求: userId={}, keywordIds={}, eventId={}",
                userId, request.getKeywordIds(), request.getEventId());
        CardService.PreviewResult result = cardService.generatePreviewJudgment(
                request.getKeywordIds(), request.getEventId());
        return Result.ok(new PreviewJudgmentVO(result.judgment()));
    }

    /**
     * POST /api/story/verdict
     * P-01: 入局判词生成（返回完整 VerdictVO，含 keywords + event）
     *
     * 请求：
     * {
     *   "keywordIds": [1, 2, 3],
     *   "eventId": 101
     * }
     *
     * 响应：
     * {
     *   "verdict": "风云将起，汝择的路，或重于泰山，或轻于鸿毛。",
     *   "keywords": "铜锁、旧伞、残笔",
     *   "event": "巨鹿·破釜沉舟"
     * }
     */
    @PostMapping("/verdict")
    public Result<VerdictVO> generateVerdict(@RequestBody VerdictRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("[P-01] 入局判词生成请求: userId={}, keywordIds={}, eventId={}",
                userId, request.getKeywordIds(), request.getEventId());

        // 查询关键词卡名称（保持传入顺序）
        List<String> keywordNames = List.of();
        if (request.getKeywordIds() != null && !request.getKeywordIds().isEmpty()) {
            try {
                List<KeywordCard> cards = keywordCardMapper.selectBatchIds(request.getKeywordIds());
                final List<Long> ids = request.getKeywordIds();
                keywordNames = cards.stream()
                        .sorted((a, b) -> Integer.compare(ids.indexOf(a.getId()), ids.indexOf(b.getId())))
                        .map(KeywordCard::getName)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("[P-01] 关键词卡查询失败: {}", e.getMessage());
            }
        }

        // 查询事件卡标题
        String eventTitle = "";
        if (request.getEventId() != null) {
            try {
                EventCard eventCard = eventCardMapper.selectById(request.getEventId());
                if (eventCard != null) {
                    eventTitle = eventCard.getTitle();
                }
            } catch (Exception e) {
                log.warn("[P-01] 事件卡查询失败: {}", e.getMessage());
            }
        }

        // 调用判官 Agent 生成判词
        JudgeAgent.VerdictResult result = judgeAgent.generateVerdict(keywordNames, eventTitle);
        return Result.ok(new VerdictVO(result.verdict(), result.keywords(), result.event()));
    }

    // ========== P-01 DTO ==========

    @Data
    public static class PreviewJudgmentRequest {
        /** 3 个关键词卡的 user_keyword_card IDs */
        private List<Long> keywordIds;
        /** 历史事件卡 ID */
        private Long eventId;
    }

    @Data
    public static class PreviewJudgmentVO {
        private String judgment;
        public PreviewJudgmentVO(String judgment) { this.judgment = judgment; }
    }

    @Data
    public static class VerdictRequest {
        private List<Long> keywordIds;
        private Long eventId;
    }

    /**
     * P-01 判词 VO
     * 返回完整的判词信息，供前端 JudgmentPreview 浮层展示
     */
    @Data
    public static class VerdictVO {
        /** 判词正文 */
        private String verdict;
        /** 关键词原文（逗号分隔） */
        private String keywords;
        /** 事件标题 */
        private String event;

        public VerdictVO(String verdict, String keywords, String event) {
            this.verdict = verdict;
            this.keywords = keywords;
            this.event = event;
        }
    }
}
