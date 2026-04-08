package com.timespace.module.story.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.service.StoryOrchestrationService;
import com.timespace.module.story.service.StoryOrchestrationService.*;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/story")
@RequiredArgsConstructor
public class StoryController {

    private final StoryOrchestrationService storyService;

    /**
     * POST /api/story/start
     * 开始新故事
     *
     * 请求：
     * {
     *   "eventCardId": 1,              // 事件卡ID
     *   "keywordCardIds": [101, 205],  // 关键词卡ID列表（2-5张）
     *   "identityType": 1,            // 1高位 2低位 3旁观者
     *   "style": 1,                   // 1白描 2江湖 3笔记 4话本
     *   "entryAnswers": {              // 入局三问
     *     "q1": "A",
     *     "q2": "B",
     *     "q3": "C"
     *   }
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "storyId": 1,
     *     "storyNo": "TS20240101123456",
     *     "title": "时光旅人手记 · 第1234号",
     *     "style": 1,
     *     "currentChapter": 1,
     *     "chapter": {
     *       "chapterNo": 1,
     *       "sceneText": "场景描写...",
     *       "options": [
     *         {"id": 1, "text": "退回马厩", "hint": "..."},
     *         {"id": 2, "text": "直面守卫", "hint": "..."},
     *         {"id": 3, "text": "翻墙逃走", "hint": "..."}
     *       ]
     *     },
     *     "characters": [...],
     *     "keywords": [...]
     *   }
     * }
     */
    @PostMapping("/start")
    public Result<StartStoryVO> startStory(@Valid @RequestBody StartStoryRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("开始故事请求: userId={}", userId);
        StartStoryVO vo = storyService.startStory(request);
        return Result.ok(vo);
    }

    /**
     * POST /api/story/:id/chapter/:no/choose
     * 提交选择（支持 WebSocket 流式）
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
     *     "chapter": {
     *       "chapterNo": 2,
     *       "sceneText": "下一章场景...",
     *       "options": [...]
     *     },
     *     "judgment": "此选择虽保全了XX，却牺牲了XX...",
     *     "isLastChapter": false
     *   }
     * }
     *
     * 【WebSocket 流式推送说明】
     *
     * 连接: /ws/story/{storyId}
     * 认证: 通过 STOMP 帧携带 token
     *
     * 推送事件:
     * - user/queue/chapter_stream: 章节内容流式片段
     *   {
     *     "storyId": 1,
     *     "chapterNo": 2,
     *     "chunk": "这是本段的文本..."
     *   }
     *
     * - user/queue/choice_result: 选择评估结果
     *   {
     *     "storyId": 1,
     *     "deviationChange": 5,
     *     "judgment": "判官判词...",
     *     "characterChanges": {...}
     *   }
     *
     * - user/queue/story_end: 故事完成
     *   {
     *     "storyId": 1,
     *     "status": "finished"
     *   }
     */
    @PostMapping("/{id}/chapter/{no}/choose")
    public Result<ChooseResultVO> submitChoice(
            @PathVariable("id") Long storyId,
            @PathVariable("no") Integer chapterNo,
            @Valid @RequestBody ChooseRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("提交选择请求: userId={}, storyId={}, chapterNo={}, optionId={}",
                userId, storyId, chapterNo, request.getOptionId());
        ChooseResultVO vo = storyService.submitChoice(storyId, chapterNo, request.getOptionId());
        return Result.ok(vo);
    }

    /**
     * POST /api/story/:id/finish
     * 结束故事，生成手稿
     *
     * 请求：空
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "storyId": 1,
     *     "manuscript": "完整小说正文...",
     *     "wordCount": 5234,
     *     "annotations": [
     *       {"chapterNo": 1, "text": "好一个'...'！", "color": "#C0392B"}
     *     ],
     *     "choiceMarks": [
     *       {"chapterNo": 1, "optionId": 2, "text": "直面守卫"}
     *     ],
     *     "epilogue": "稗官曰...",
     *     "historyDeviation": 65
     *   }
     * }
     */
    @PostMapping("/{id}/finish")
    public Result<FinishStoryVO> finishStory(@PathVariable("id") Long storyId) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("结束故事请求: userId={}, storyId={}", userId, storyId);
        FinishStoryVO vo = storyService.finishStory(storyId);
        return Result.ok(vo);
    }

    /**
     * GET /api/story/:id
     * 获取故事详情
     */
    @GetMapping("/{id}")
    public Result<StoryDetailVO> getStoryDetail(@PathVariable("id") Long storyId) {
        StoryDetailVO vo = storyService.getStoryDetail(storyId);
        return Result.ok(vo);
    }

    /**
     * GET /api/story/list
     * 获取用户的故事列表
     */
    @GetMapping("/list")
    public Result<List<StoryListItemVO>> getStoryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long userId = StpUtil.getLoginIdAsLong();
        // TODO: 分页查询
        return Result.ok(List.of());
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
}
