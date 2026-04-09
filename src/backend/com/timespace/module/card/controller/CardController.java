package com.timespace.module.card.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.card.service.CardService;
import com.timespace.module.card.service.CardService.DrawResult;
import com.timespace.module.card.service.CardService.UserCardVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /**
     * POST /api/card/draw/keyword
     * 抽关键词卡
     *
     * 请求：
     * {
     *   "useFreeDraw": false  // true=免费，false=消耗墨晶
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "cardId": 101,
     *     "cardNo": "K00010001",
     *     "name": "铜锁",
     *     "category": 1,
     *     "rarity": 1,
     *     "description": "一把锈迹斑斑的铜锁...",
     *     "imageUrl": "https://...",
     *     "inkFragrance": 7,
     *     "isGuaranteedRare": false
     *   }
     * }
     *
     * 【抽卡算法详解】
     *
     * 1. 扣费检查：
     *    - useFreeDraw=true：检查 user.dailyFreeDraws > 0
     *    - useFreeDraw=false：检查 user.inkStone >= 10
     *    - 均不满足返回 400 "墨晶不足" 或 "今日免费次数已用完"
     *
     * 2. 分布式锁：
     *    - 使用 Redisson 实现分布式锁（key = draw:lock:user:{userId}）
     *    - 锁超时 10 秒，防止死锁
     *    - 防止同一用户并发抽卡导致状态不一致
     *
     * 3. 保底机制：
     *    - 连续9次未出奇品 → 第10抽必出奇品（保底奇品）
     *    - 连续29次未出绝品 → 第30抽必出绝品（保底绝品）
     *    - 奇品保底优先级 > 绝品保底
     *    - 保底概率 100%（确定性，非概率叠加）
     *
     * 4. 基础概率：
     *    - 凡品(1)：80%
     *    - 珍品(2)：15%
     *    - 奇品(3)：4%
     *    - 绝品(4)：1%
     *
     * 5. 桶排加权算法：
     *    - 将所有卡牌按权重展开到数组
     *    - ThreadLocalRandom.nextInt 随机选择下标
     *    - 时间复杂度 O(1)，空间换时间
     *
     * 6. 保底状态存储：
     *    - Redis Key: card:guarantee:user:{userId}
     *    - TTL: 7天
     *    - 出奇品 → 只重置奇品保底计数器
     *    - 出绝品 → 重置两个计数器
     *
     * 7. 墨香值处理：
     *    - 新卡默认 inkFragrance = 7
     *    - 重复抽中 → inkFragrance +1（最高7）
     *    - 故事中触发共鸣 → inkFragrance +1（最高7）
     */
    @PostMapping("/draw/keyword")
    public Result<DrawResult> drawKeywordCard(@RequestBody DrawKeywordRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("抽关键词卡请求: userId={}, useFreeDraw={}", userId, request.isUseFreeDraw());
        DrawResult result = cardService.drawKeywordCard(userId, request.isUseFreeDraw());
        return Result.ok(result);
    }

    /**
     * GET /api/card/owned
     * 获取用户拥有的关键词卡列表
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": [
     *     {
     *       "userCardId": 1,
     *       "cardId": 101,
     *       "cardNo": "K00010001",
     *       "name": "铜锁",
     *       "category": 1,
     *       "rarity": 1,
     *       "description": "...",
     *       "imageUrl": "https://...",
     *       "inkFragrance": 7,
     *       "resonanceCount": 3,
     *       "acquiredAt": "2024-01-01T10:00:00"
     *     }
     *   ]
     * }
     */
    @GetMapping("/owned")
    public Result<List<UserCardVO>> getOwnedCards(
            @RequestParam(required = false) Integer category,
            @RequestParam(required = false) Integer rarity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long userId = StpUtil.getLoginIdAsLong();
        List<UserCardVO> cards = cardService.getUserCards(userId, category, rarity, page, size);
        return Result.ok(cards);
    }

    /**
     * GET /api/card/{cardId}/detail
     * 获取卡牌详情
     */
    @GetMapping("/{cardId}/detail")
    public Result<UserCardVO> getCardDetail(@PathVariable Long cardId) {
        long userId = StpUtil.getLoginIdAsLong();
        List<UserCardVO> cards = cardService.getUserCards(userId, null, null, 1, 100);
        UserCardVO card = cards.stream()
                .filter(c -> c.getCardId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new com.timespace.common.exception.BusinessException(404, "卡牌不存在"));
        return Result.ok(card);
    }

    /**
     * GET /api/card/fortune
     * 墨迹占卜（今日运势）
     * 根据当天日期 seed + 用户 ID 哈希，从预置运势文案中选取一条
     * 同一用户同一天看到的运势相同，不同日期或用户看到的运势不同
     */
    @GetMapping("/fortune")
    public Result<CardService.FortuneResult> getFortune() {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("墨迹占卜请求: userId={}", userId);
        CardService.FortuneResult result = cardService.getFortune(userId);
        return Result.ok(result);
    }

    /**
     * POST /api/card/draw/event
     * 抽事件卡
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "cardId": 1,
     *     "cardNo": "EV001",
     *     "title": "巨鹿·破釜沉舟",
     *     "dynasty": "秦",
     *     "location": "巨鹿",
     *     "description": "项羽率楚军渡河...",
     *     "era": "秦末",
     *     "isGuaranteedRare": false
     *   }
     * }
     *
     * 【事件卡抽卡算法】
     * - 复用 DrawAlgorithm 权重桶算法
     * - 事件卡独立保底计数器（Redis key 加 event: 前缀）
     * - 事件卡保底：连续9次未出珍品，第10抽必出珍品（简化版保底）
     * - 事件卡无免费次数限制（每日可抽任意次）
     */
    @PostMapping("/draw/event")
    public Result<CardService.DrawEventResult> drawEventCard() {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("抽事件卡请求: userId={}", userId);
        CardService.DrawEventResult result = cardService.drawEventCard(userId);
        return Result.ok(result);
    }

    @Data
    public static class DrawKeywordRequest {
        private boolean useFreeDraw;
    }

    // ========== P-01 组合判词生成 ========== //

    /**
     * POST /api/card/preview
     * 选完3张关键词+1事件后，点击"入局"前，调用 AI 生成一句古文判词（20字以内）
     *
     * 请求：
     * {
     *   "keywordIds": [1, 2, 3],
     *   "eventId": 1
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "data": {
     *     "judgment": "江边渡口，有人把半生未说出口的话，折成一张旧船票。"
     *   }
     * }
     */
    @PostMapping("/preview")
    public Result<CardService.PreviewResult> preview(@RequestBody CardService.PreviewRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("组合判词预览请求: userId={}, keywordIds={}, eventId={}",
                userId, request.getKeywordIds(), request.getEventId());
        CardService.PreviewResult result = cardService.generatePreviewJudgment(
                request.getKeywordIds(), request.getEventId());
        return Result.ok(result);
    }
}
