package com.timespace.module.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.common.utils.IdGenerator;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 抽卡算法 + 保底机制
 *
 * 稀有度等级：
 * 1 = 凡品（common）
 * 2 = 珍品（rare）
 * 3 = 奇品（epic）    — 保底第10抽必出
 * 4 = 绝品（legendary） — 保底第30抽必出
 *
 * 保底规则：
 * - 基础概率：凡品80%，珍品15%，奇品4%，绝品1%
 * - 保底计数器：每抽累计
 * - 连续9次（10抽内）未出奇品 → 第10抽保底奇品
 * - 连续29次（30抽内）未出绝品 → 第30抽保底绝品
 * - 奇品保底优先级高于绝品保底
 * - 出奇品 → 重置奇品保底计数器（保留绝品计数器）
 * - 出绝品 → 重置两个计数器
 *
 * 桶排算法：
 * - 将所有卡牌按权重展开到数组
 * - Java随机选择下标，时间复杂度O(1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawAlgorithm {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ====== 可配置概率参数 ======
    @Value("${timespace.card.guaranteed-rare-chapter:10}")
    private int guaranteedRareChapter; // 奇品保底章节数（10抽）

    @Value("${timespace.card.guaranteed-legendary-chapter:30}")
    private int guaranteedLegendaryChapter; // 绝品保底章节数（30抽）

    // ====== Redis Key ======
    private static final String GUARANTEE_STATE_KEY = "card:guarantee:user:%d";
    private static final long GUARANTEE_STATE_TTL = 7 * 24 * 3600L; // 7天

    // ====== 稀有度定义 ======
    public static final int RARITY_COMMON = 1;     // 凡品
    public static final int RARITY_RARE = 2;       // 珍品
    public static final int RARITY_EPIC = 3;        // 奇品
    public static final int RARITY_LEGENDARY = 4;  // 绝品

    // ====== 基础概率（百分比）======
    private static final int BASE_RATE_COMMON = 80;
    private static final int BASE_RATE_RARE = 15;
    private static final int BASE_RATE_EPIC = 4;
    private static final int BASE_RATE_LEGENDARY = 1;

    /**
     * 保底状态（存储在Redis中）
     */
    public static class GuaranteeState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public int consecutiveNonEpic;     // 连续未出奇品次数
        public int consecutiveNonLegendary;// 连续未出绝品次数
        public long lastDrawTime;          // 上次抽卡时间戳

        public GuaranteeState() {
            this.consecutiveNonEpic = 0;
            this.consecutiveNonLegendary = 0;
            this.lastDrawTime = 0;
        }
    }

    /**
     * 获取用户的保底状态
     */
    public GuaranteeState getGuaranteeState(Long userId) {
        String key = String.format(GUARANTEE_STATE_KEY, userId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return new GuaranteeState();
        }
        try {
            return MAPPER.readValue(json, GuaranteeState.class);
        } catch (Exception e) {
            log.error("解析保底状态失败: userId={}", userId, e);
            return new GuaranteeState();
        }
    }

    /**
     * 更新保底状态
     */
    public void updateGuaranteeState(Long userId, GuaranteeState state, int drawnRarity) {
        // 更新计数器
        if (drawnRarity < RARITY_EPIC) {
            state.consecutiveNonEpic++;
        } else {
            state.consecutiveNonEpic = 0; // 出奇品，重置
        }

        if (drawnRarity < RARITY_LEGENDARY) {
            state.consecutiveNonLegendary++;
        } else {
            state.consecutiveNonLegendary = 0; // 出绝品，两个都重置
        }

        state.lastDrawTime = System.currentTimeMillis();

        // 写回Redis
        String key = String.format(GUARANTEE_STATE_KEY, userId);
        try {
            String json = MAPPER.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, GUARANTEE_STATE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("保存保底状态失败: userId={}", userId, e);
        }

        log.info("保底状态更新: userId={}, consecutiveNonEpic={}, consecutiveNonLegendary={}, drawnRarity={}",
                userId, state.consecutiveNonEpic, state.consecutiveNonLegendary, drawnRarity);
    }

    /**
     * 执行单抽
     *
     * 核心算法流程：
     * 1. 检查保底（优先高优先级）
     *    - 如果连续未出奇品 >= (保底章节数-1)，则保底奇品
     *    - 否则，如果连续未出绝品 >= (保底章节数-1)，则保底绝品
     * 2. 若无保底，使用加权随机算法选择稀有度
     * 3. 根据稀有度从对应池中加权随机选择具体卡牌
     *
     * @param state 当前保底状态
     * @return 抽中的卡牌
     */
    public KeywordCard executeDraw(GuaranteeState state) {
        int rarity = determineRarity(state);
        log.debug("抽卡结果: rarity={}, consecutiveNonEpic={}, consecutiveNonLegendary={}",
                rarity, state.consecutiveNonEpic, state.consecutiveNonLegendary);
        return selectCardByRarity(rarity);
    }

    /**
     * 确定稀有度
     */
    private int determineRarity(GuaranteeState state) {
        // ====== 1. 检查保底 ======

        // 奇品保底：连续(保底章节数-1)次未出，第保底章节数必出
        // 例如：guaranteedRareChapter=10，则第10抽必出
        if (state.consecutiveNonEpic >= (guaranteedRareChapter - 1)) {
            log.info("触发奇品保底: consecutiveNonEpic={}", state.consecutiveNonEpic);
            return RARITY_EPIC;
        }

        // 绝品保底：连续(保底章节数-1)次未出，第保底章节数必出
        // 注意：奇品保底优先级高于绝品保底（因为先检查）
        if (state.consecutiveNonLegendary >= (guaranteedLegendaryChapter - 1)) {
            log.info("触发绝品保底: consecutiveNonLegendary={}", state.consecutiveNonLegendary);
            return RARITY_LEGENDARY;
        }

        // ====== 2. 加权随机 ======
        // 将概率区间分段：[0,80)=凡品，[80,95)=珍品，[95,99)=奇品，[99,100)=绝品
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < BASE_RATE_COMMON) {
            return RARITY_COMMON;
        } else if (roll < BASE_RATE_COMMON + BASE_RATE_RARE) {
            return RARITY_RARE;
        } else if (roll < BASE_RATE_COMMON + BASE_RATE_RARE + BASE_RATE_EPIC) {
            return RARITY_EPIC;
        } else {
            return RARITY_LEGENDARY;
        }
    }

    /**
     * 根据稀有度从数据库中加权随机选择一张卡
     */
    private KeywordCard selectCardByRarity(int rarity) {
        // 实际项目中从数据库查询对应稀有度的卡牌并按权重加权随机
        // 这里返回模拟卡牌
        KeywordCard card = new KeywordCard();
        card.setId((long) (Math.random() * 1000));
        card.setCardNo(IdGenerator.cardNo(1, card.getId()));
        card.setRarity(rarity);
        card.setName(getRandomKeyword(rarity));
        card.setCategory((int) (Math.random() * 5) + 1);
        return card;
    }

    /**
     * 执行多抽（通常是10连）
     * 保证至少一张珍品或以上
     */
    public List<KeywordCard> executeMultiDraw(GuaranteeState state, int count) {
        List<KeywordCard> results = new ArrayList<>(count);
        KeywordCard guaranteed = null;

        // 先确保至少一张珍品或以上
        for (int i = 0; i < count; i++) {
            GuaranteeState drawState = new GuaranteeState();
            // 多抽时保底进度共享
            drawState.consecutiveNonEpic = state.consecutiveNonEpic;
            drawState.consecutiveNonLegendary = state.consecutiveNonLegendary;

            KeywordCard card = executeDraw(drawState);

            // 保底至少一张珍品
            if (guaranteed == null && card.getRarity() >= RARITY_RARE) {
                guaranteed = card;
            }

            results.add(card);
            state.consecutiveNonEpic = drawState.consecutiveNonEpic;
            state.consecutiveNonLegendary = drawState.consecutiveNonLegendary;
        }

        // 如果没有珍品以上，保底替换最后一张
        if (guaranteed == null) {
            results.set(count - 1, forceRarityDraw(RARITY_RARE));
        }

        return results;
    }

    /**
     * 强制指定稀有度抽卡（用于保底）
     */
    private KeywordCard forceRarityDraw(int targetRarity) {
        KeywordCard card = new KeywordCard();
        card.setId((long) (Math.random() * 1000));
        card.setCardNo(IdGenerator.cardNo(1, card.getId()));
        card.setRarity(targetRarity);
        card.setName(getRandomKeyword(targetRarity));
        card.setCategory((int) (Math.random() * 5) + 1);
        return card;
    }

    /**
     * 获取随机关键词（模拟）
     */
    private String getRandomKeyword(int rarity) {
        String[][] keywords = {
                // 凡品
                {"铜锁", "油灯", "旧伞", "粗瓷碗", "木梳", "布鞋", "草帽", "麻绳", "纸扇", "陶罐"},
                // 珍品
                {"玉佩", "银簪", "古琴", "砚台", "铜镜", "香囊", "折扇", "玉镯"},
                // 奇品
                {"虎符", "令牌", "凤冠", "剑穗", "罗盘", "玦", "鼎", "爵"},
                // 绝品
                {"河图", "洛书", "和氏璧", "九锡", "传国玉玺", "太阿剑"}
        };
        String[] pool = keywords[rarity - 1];
        return pool[(int) (Math.random() * pool.length)];
    }

    /**
     * 重置保底状态（用于测试或特殊场景）
     */
    public void resetGuaranteeState(Long userId) {
        String key = String.format(GUARANTEE_STATE_KEY, userId);
        redisTemplate.delete(key);
        log.info("保底状态已重置: userId={}", userId);
    }
}
