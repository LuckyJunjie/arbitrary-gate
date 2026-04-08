package com.timespace.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

import java.util.UUID;

public class IdGenerator {

    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    /**
     * 生成19位Long类型ID（基于Snowflake）
     */
    public static long nextId() {
        return snowflake.nextId();
    }

    /**
     * 生成UUID（带连字符）
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成简短UUID（不带连字符）
     */
    public static String simpleUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成故事编号（格式：TS + 年月日 + 6位随机数）
     */
    public static String storyNo() {
        return "TS" + cn.hutool.core.date.DateUtil.format(cn.hutool.core.date.DateUtil.date(), "yyyyMMdd")
                + cn.hutool.core.util.RandomUtil.randomNumbers(6);
    }

    /**
     * 生成分享码（8位字母数字混合，不含易混淆字符）
     */
    public static String shareCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 排除 I,O,0,1
        return cn.hutool.core.util.RandomUtil.randomString(chars, 8);
    }

    /**
     * 生成卡牌编号（格式：K + 4位类别 + 4位序号）
     */
    public static String cardNo(int category, long id) {
        return String.format("K%04d%04d", category, id % 10000);
    }
}
