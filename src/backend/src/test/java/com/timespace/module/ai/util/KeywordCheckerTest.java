package com.timespace.module.ai.util;

import com.timespace.module.card.entity.KeywordCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeywordChecker 关键词融入率检测 单元测试
 *
 * 测试覆盖：
 * - 正常手稿中关键词全部融入的情况
 * - 手稿中缺少一个或多个关键词的情况
 * - 空手稿或空关键词列表的边界情况
 * - emphasisPrompt 生成逻辑
 * - 中文关键词精确匹配
 */
class KeywordCheckerTest {

    // ─── checkManuscript(String, List<KeywordCard>) 测试 ──────────────────────────────────

    @Test
    void checkManuscript_全部融入() {
        String manuscript = "赤壁江边，东风骤起。一把旧船票握在手心，铜锁芯已经生锈。摆渡人站在船头。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertTrue(result.isAllIntegrated());
        assertEquals(0, result.getMissingKeywords().size());
        assertEquals(1, result.getKeywordCounts().get("旧船票"));
        assertEquals(1, result.getKeywordCounts().get("铜锁芯"));
        assertEquals(1, result.getKeywordCounts().get("摆渡人"));
        assertEquals("", result.getEmphasisPrompt());
    }

    @Test
    void checkManuscript_缺少一个关键词() {
        String manuscript = "赤壁江边，东风骤起。一把旧船票握在手心，铜锁芯已经生锈。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertFalse(result.isAllIntegrated());
        assertEquals(1, result.getMissingKeywords().size());
        assertTrue(result.getMissingKeywords().contains("摆渡人"));
        assertEquals(1, result.getKeywordCounts().get("旧船票"));
        assertEquals(1, result.getKeywordCounts().get("铜锁芯"));
        assertEquals(0, result.getKeywordCounts().get("摆渡人"));
        assertTrue(result.getEmphasisPrompt().contains("摆渡人"));
    }

    @Test
    void checkManuscript_缺少多个关键词() {
        String manuscript = "江风扑面而来，";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertFalse(result.isAllIntegrated());
        assertEquals(3, result.getMissingKeywords().size());
        assertTrue(result.getMissingKeywords().containsAll(List.of("旧船票", "铜锁芯", "摆渡人")));
        assertFalse(result.getEmphasisPrompt().isEmpty());
    }

    @Test
    void checkManuscript_空手稿() {
        String manuscript = "";
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertFalse(result.isAllIntegrated());
        assertEquals(1, result.getMissingKeywords().size());
        assertTrue(result.getMissingKeywords().contains("旧船票"));
    }

    @Test
    void checkManuscript_null手稿() {
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(null, keywords);

        assertFalse(result.isAllIntegrated());
        assertEquals(1, result.getMissingKeywords().size());
    }

    @Test
    void checkManuscript_空关键词列表() {
        String manuscript = "赤壁江边，东风骤起。";

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, List.of());

        assertTrue(result.isAllIntegrated());
        assertTrue(result.getMissingKeywords().isEmpty());
        assertEquals("", result.getEmphasisPrompt());
    }

    @Test
    void checkManuscript_null关键词列表() {
        String manuscript = "赤壁江边，东风骤起。";

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, (List<KeywordCard>) null);

        // 空列表视为全部融入
        assertTrue(result.isAllIntegrated());
        assertTrue(result.getMissingKeywords().isEmpty());
    }

    @Test
    void checkManuscript_关键词出现多次() {
        String manuscript = "他握着一把旧船票，旧船票上写着名字。旧船票是唯一的凭证。";
        List<KeywordCard> keywords = List.of(card(1L, "旧船票"));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertTrue(result.isAllIntegrated());
        assertEquals(3, result.getKeywordCounts().get("旧船票"));
        assertEquals(0, result.getMissingKeywords().size());
    }

    @Test
    void checkManuscript_关键词是短语() {
        String manuscript = "破釜沉舟是项羽的典故。";
        List<KeywordCard> keywords = List.of(card(1L, "破釜沉舟"));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertTrue(result.isAllIntegrated());
        assertEquals(1, result.getKeywordCounts().get("破釜沉舟"));
    }

    // ─── checkManuscript(String, List<String>) 测试 ───────────────────────────────────────

    @Test
    void checkManuscript_byNames_全部融入() {
        String manuscript = "江风扑面，带着水草的气息。";
        List<String> keywordNames = List.of("江风", "水草");

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywordNames);

        assertTrue(result.isAllIntegrated());
        assertTrue(result.getMissingKeywords().isEmpty());
    }

    @Test
    void checkManuscript_byNames_缺少一个() {
        String manuscript = "江风扑面，带着水草的气息。";
        List<String> keywordNames = List.of("江风", "烈日");

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywordNames);

        assertFalse(result.isAllIntegrated());
        assertEquals(1, result.getMissingKeywords().size());
        assertTrue(result.getMissingKeywords().contains("烈日"));
    }

    // ─── emphasisPrompt 测试 ─────────────────────────────────────────────────────────────────

    @Test
    void emphasisPrompt_包含缺失关键词() {
        String manuscript = "只有旧船票，没有铜锁芯和摆渡人";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        String prompt = result.getEmphasisPrompt();
        assertFalse(prompt.isEmpty());
        assertTrue(prompt.contains("铜锁芯"));
        assertTrue(prompt.contains("摆渡人"));
        assertFalse(prompt.contains("旧船票")); // 旧船票已融入，不应强调
    }

    @Test
    void emphasisPrompt_空字符串当全部融入时() {
        String manuscript = "旧船票、铜锁芯、摆渡人都在。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertTrue(result.isAllIntegrated());
        assertEquals("", result.getEmphasisPrompt());
    }

    @Test
    void emphasisPrompt_多个缺失用顿号分隔() {
        String manuscript = "只有旧船票。";
        List<KeywordCard> keywords = List.of(
                card(1L, "旧船票"),
                card(2L, "铜锁芯"),
                card(3L, "摆渡人"),
                card(4L, "明月")
        );

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        String prompt = result.getEmphasisPrompt();
        assertTrue(prompt.contains("铜锁芯"));
        assertTrue(prompt.contains("摆渡人"));
        assertTrue(prompt.contains("明月"));
    }

    // ─── 边界条件测试 ─────────────────────────────────────────────────────────────────────

    @Test
    void checkManuscript_关键词包含正则特殊字符() {
        // 关键词中包含 . * 等正则特殊字符
        String manuscript = "铜锁芯在火中融化。";
        List<KeywordCard> keywords = List.of(card(1L, "铜锁芯.")); // 含 . 

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        // 应该精确匹配 "铜锁芯." 而不是任意字符
        assertFalse(result.isAllIntegrated()); // 原文没有 "铜锁芯."
        assertEquals(0, result.getKeywordCounts().get("铜锁芯."));
    }

    @Test
    void checkManuscript_关键词为空字符串() {
        String manuscript = "赤壁江边。";
        List<KeywordCard> keywords = List.of(card(1L, ""));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        // 空关键词不计入缺失
        assertTrue(result.getMissingKeywords().isEmpty());
    }

    @Test
    void checkManuscript_关键词为空白字符() {
        String manuscript = "赤壁江边。";
        List<KeywordCard> keywords = List.of(card(1L, "   "));

        KeywordChecker.CheckResult result = KeywordChecker.checkManuscript(manuscript, keywords);

        assertTrue(result.getMissingKeywords().isEmpty());
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────────────────

    private KeywordCard card(Long id, String name) {
        return new KeywordCard() {{
            setId(id);
            setName(name);
        }};
    }
}
