package com.timespace.module.card.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * C-11 墨香衰减配置
 * 支持通过 application.yml 配置衰减速率和最低下限
 */
@Data
@Component
@ConfigurationProperties(prefix = "timespace.ink.decay")
public class InkDecayConfig {

    /** 是否启用墨香衰减 */
    private boolean enabled = true;

    /** 每小时衰减百分比（5 = 5% per hour） */
    private double rate = 5.0;

    /** 最低衰减模式：base = 稀有度基准值 */
    private String minimum = "base";

    /** 稀有度对应基准墨香值（衰减下限） */
    private Map<Integer, Integer> rarityBase = Map.of(
            1, 0,   // 凡
            2, 1,   // 珍
            3, 2,   // 奇
            4, 3    // 绝
    );

    /**
     * 获取指定稀有度的基准墨香值（衰减下限）
     */
    public int getBaseForRarity(int rarity) {
        return rarityBase.getOrDefault(rarity, 0);
    }

    /**
     * 计算衰减后墨香值
     * @param current 当前墨香值
     * @param rarity 稀有度
     * @param hoursElapsed 过去小时数
     * @return 衰减后墨香值（不低于基准值）
     */
    public int calculateDecayedValue(int current, int rarity, double hoursElapsed) {
        if (!enabled || hoursElapsed <= 0) {
            return current;
        }
        int base = getBaseForRarity(rarity);
        // 每小时衰减 rate%，线性衰减
        double decayPerHour = rate / 100.0;
        double decayed = current - (hoursElapsed * decayPerHour * current);
        return (int) Math.max(base, Math.round(decayed));
    }
}
