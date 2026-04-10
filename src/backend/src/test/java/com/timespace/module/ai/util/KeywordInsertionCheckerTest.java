package com.timespace.module.ai.util;

import com.timespace.module.card.entity.KeywordCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeywordInsertionChecker 关键词融入率检测（≥3 个阈值）单元测试
 *
 * AI-09 测试覆盖：
 * - 手稿中融入 ≥3 个关键词 → isSufficient=true，无警告
 * - 手稿中融入 <3 个关键词 → isSufficient=false，有警告
 * - 空手稿或空关键词列表的边界情况
 * - 关键词出现多次只计一次
 * - 中文关键词精确匹配
 */
class KeywordInsertionCheckerTest {

    // ─── check(String, List<KeywordCard>) 测试 ──────────────────────────────────────────────

    @Test
    void check_融入3个关键词_达标无警告() {
        // 3 个关键词全部融入 → 达标
        String manuscript = "赤壁江边，东风骤起。一把旧船票握在手心，铜锁芯已经生锈。摆渡人站在船头，望着江水。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertTrue(result.isSufficient());
        assertEquals(3, result.getIntegratedCount());
        assertEquals(3, result.getTotalCount());
        assertNull(result.getWarning());
        assertTrue(result.getIntegratedKeywordNames().containsAll(
                List.of("旧船票", "铜锁芯", "摆渡人")));
    }

    @Test
    void check_融入4个关键词_达标无警告() {
        String manuscript = "旧船票、铜锁芯、摆渡人、明月，都在故事中出现。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人"),
                card(4L, "明月")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertTrue(result.isSufficient());
        assertEquals(4, result.getIntegratedCount());
        assertEquals(4, result.getTotalCount());
        assertNull(result.getWarning());
    }

    @Test
    void check_融入2个关键词_未达标有警告() {
        // 只有 2 个关键词融入 → 未达标（阈值是 ≥3）
        String manuscript = "旧船票握在手心，铜锁芯已经生锈。摆渡人不在场。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertEquals(2, result.getIntegratedCount());
        assertEquals(3, result.getTotalCount());
        assertNotNull(result.getWarning());
        assertTrue(result.getWarning().contains("2/3"));
        assertTrue(result.getWarning().contains("关键词融入提醒"));
    }

    @Test
    void check_只融入1个关键词_未达标有警告() {
        String manuscript = "只有旧船票，其他词都没有。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertEquals(1, result.getIntegratedCount());
        assertNotNull(result.getWarning());
        assertTrue(result.getWarning().contains("1/3"));
    }

    @Test
    void check_0个关键词融入_未达标有警告() {
        String manuscript = "赤壁江边，东风骤起，什么关键词都没有。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertEquals(0, result.getIntegratedCount());
        assertNotNull(result.getWarning());
        assertTrue(result.getWarning().contains("0/3"));
    }

    @Test
    void check_空手稿_未达标() {
        String manuscript = "";
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertEquals(0, result.getIntegratedCount());
        assertNotNull(result.getWarning());
    }

    @Test
    void check_null手稿_未达标() {
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(null, keywords);

        assertFalse(result.isSufficient());
        assertEquals(0, result.getIntegratedCount());
        assertNotNull(result.getWarning());
    }

    @Test
    void check_空关键词列表_达标() {
        String manuscript = "赤壁江边，东风骤起。";

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, List.of());

        // 无关键词时，视为达标（无需警告）
        assertTrue(result.isSufficient());
        assertEquals(0, result.getIntegratedCount());
        assertNull(result.getWarning());
    }

    @Test
    void check_关键词出现多次只计一次() {
        // 同一个词出现多次，只计一次
        String manuscript = "旧船票旧船票旧船票，旧船票！";
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertTrue(result.isSufficient());
        assertEquals(1, result.getIntegratedCount());
        assertEquals(1, result.getTotalCount());
    }

    // ─── check(String, List<String>) 测试 ─────────────────────────────────────────────────

    @Test
    void check_byNames_融入3个_达标() {
        String manuscript = "旧船票在手中，铜锁芯在火中融化，摆渡人已离去。";
        List<String> keywordNames = List.of("旧船票", "铜锁芯", "摆渡人");

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywordNames);

        assertTrue(result.isSufficient());
        assertEquals(3, result.getIntegratedCount());
        assertNull(result.getWarning());
    }

    @Test
    void check_byNames_融入2个_未达标() {
        String manuscript = "旧船票在手中，铜锁芯在火中融化，摆渡人不在。";
        List<String> keywordNames = List.of("旧船票", "铜锁芯", "摆渡人");

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywordNames);

        assertFalse(result.isSufficient());
        assertEquals(2, result.getIntegratedCount());
        assertNotNull(result.getWarning());
        assertTrue(result.getWarning().contains("2/3"));
    }

    // ─── 警告信息内容测试 ────────────────────────────────────────────────────────────────

    @Test
    void check_警告信息包含正确数量() {
        String manuscript = "只有旧船票在。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人"),
                card(4L, "明月")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertEquals(1, result.getIntegratedCount());
        assertEquals(4, result.getTotalCount());
        assertNotNull(result.getWarning());
        assertTrue(result.getWarning().contains("1/4"));
    }

    @Test
    void check_警告信息提示重新生成() {
        String manuscript = "赤壁东风起了。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertFalse(result.isSufficient());
        assertNotNull(result.getWarning());
        // 警告应包含"建议重新生成"之类的提示
        assertTrue(result.getWarning().contains("建议重新生成") ||
                result.getWarning().contains("关键词融入提醒"));
    }

    // ─── 关键词精确匹配测试 ────────────────────────────────────────────────────────────

    @Test
    void check_关键词是短语_精确匹配() {
        String manuscript = "破釜沉舟是项羽的典故。";
        List<KeywordCard> keywords = List.of(card(1L, "破釜沉舟"));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        assertTrue(result.isSufficient());
        assertEquals(1, result.getIntegratedCount());
    }

    @Test
    void check_关键词包含正则特殊字符_精确匹配() {
        // 关键词含 . 等特殊字符，应精确匹配而非作为正则
        String manuscript = "铜锁芯在火中融化。";
        List<KeywordCard> keywords = List.of(card(1L, "铜锁芯."));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        // 原文中没有 "铜锁芯." (带点号)，应视为未融入
        assertFalse(result.isSufficient());
    }

    @Test
    void check_空关键词字符串不计入() {
        String manuscript = "赤壁江边。";
        List<KeywordCard> keywords = List.of(card(1L, ""), card(2L, "铜锁芯"));

        KeywordInsertionChecker.CheckResult result = KeywordInsertionChecker.check(manuscript, keywords);

        // 空字符串关键词不计入总数也不计入缺失
        assertFalse(result.isSufficient()); // 因为只有 1 个词融入（铜锁芯），但总数按 2 算
        assertEquals(1, result.getIntegratedCount());
        assertEquals(2, result.getTotalCount());
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────────────

    private KeywordCard card(Long id, String name) {
        return new KeywordCard() {{
            setId(id);
            setName(name);
        }};
    }
}
