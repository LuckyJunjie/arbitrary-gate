package com.timespace.module.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 掌眼 Agent 单元测试
 *
 * 测试覆盖：
 * - filter() 方法：黑名单词汇删除/替换
 * - findBlacklistWords()：正确识别黑名单词
 * - 正常文本不受影响
 * - 边界条件处理
 */
class ZhangyanAgentTest {

    // ZhangyanAgent 依赖 AIClient，但 filter 是纯函数，
    // 测试时直接实例化（aiClient 字段不会用于 filter）

    private ZhangyanAgent newAgent() {
        return new ZhangyanAgent(null);
    }

    // ─── filter() 测试 ──────────────────────────────────────────────

    @Test
    void filter_删除不禁() {
        String result = newAgent().filter("他不禁流下眼泪。");
        assertFalse(result.contains("不禁"));
        assertTrue(result.contains("流下眼泪"));
    }

    @Test
    void filter_替换宛如为像() {
        String result = newAgent().filter("水面宛如镜子。");
        assertFalse(result.contains("宛如"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换仿佛为好像() {
        String result = newAgent().filter("远处仿佛有灯火。");
        assertFalse(result.contains("仿佛"));
        assertTrue(result.contains("好像"));
    }

    @Test
    void filter_替换无法言说为说不清() {
        String result = newAgent().filter("心中无法言说的惆怅。");
        assertFalse(result.contains("无法言说"));
        assertTrue(result.contains("说不清"));
    }

    @Test
    void filter_替换缓缓说道为说() {
        String result = newAgent().filter("老人缓缓说道。");
        assertFalse(result.contains("缓缓说道"));
        assertTrue(result.contains("老人说"));
    }

    @Test
    void filter_替换轻声说道为低声道() {
        String result = newAgent().filter("她轻声说道。");
        assertFalse(result.contains("轻声说道"));
        assertTrue(result.contains("低声道"));
    }

    @Test
    void filter_替换目光中满是为眼里尽是() {
        String result = newAgent().filter("他目光中满是期待。");
        assertFalse(result.contains("目光中满是"));
        assertTrue(result.contains("眼里尽是"));
    }

    @Test
    void filter_替换心中一动为心头一紧() {
        String result = newAgent().filter("他心中一动。");
        assertFalse(result.contains("心中一动"));
        assertTrue(result.contains("心头一紧"));
    }

    @Test
    void filter_替换似乎在诉说为像是在说() {
        String result = newAgent().filter("远山似乎在诉说。");
        assertFalse(result.contains("似乎在诉说"));
        assertTrue(result.contains("像是在说"));
    }

    @Test
    void filter_多词共存全部处理() {
        String input = "他目光中满是期待，不禁轻声说道：\"宛如梦境，仿佛隔世。\"";
        String result = newAgent().filter(input);
        assertFalse(result.contains("不禁"));
        assertFalse(result.contains("宛如"));
        assertFalse(result.contains("仿佛"));
        assertFalse(result.contains("轻声说道"));
        assertFalse(result.contains("目光中满是"));
        assertTrue(result.contains("眼里尽是期待"));
        assertTrue(result.contains("低声道"));
    }

    @Test
    void filter_清理连续空格() {
        String result = newAgent().filter("老人说，  他走远了。");
        assertFalse(result.contains("  "));
    }

    @Test
    void filter_清理句首残留标点() {
        assertEquals("他说", newAgent().filter("，他说"));
        assertEquals("他说", newAgent().filter("。他说"));
    }

    @Test
    void filter_正常文本不受影响() {
        String input = "江风扑面，带着水草与焦木的气息。你站在赤壁对岸的山崖上。";
        String result = newAgent().filter(input);
        assertEquals(input, result);
    }

    @Test
    void filter_空字符串返回空() {
        assertEquals("", newAgent().filter(""));
    }

    @Test
    void filter_null返回null() {
        assertNull(newAgent().filter(null));
    }

    // ─── findBlacklistWords() 测试 ──────────────────────────────────

    @Test
    void findBlacklistWords_识别单个词() {
        List<String> result = newAgent().findBlacklistWords("他目光中满是期待。");
        assertTrue(result.contains("目光中满是"));
    }

    @Test
    void findBlacklistWords_识别多个词() {
        List<String> result = newAgent().findBlacklistWords("他不禁轻声说道，宛如梦境。");
        assertTrue(result.contains("不禁"));
        assertTrue(result.contains("轻声说道"));
        assertTrue(result.contains("宛如"));
    }

    @Test
    void findBlacklistWords_无黑名单词返回空列表() {
        List<String> result = newAgent().findBlacklistWords("江风扑面，水草气息。");
        assertTrue(result.isEmpty());
    }

    @Test
    void findBlacklistWords_空字符串返回空列表() {
        List<String> result = newAgent().findBlacklistWords("");
        assertTrue(result.isEmpty());
    }

    @Test
    void findBlacklistWords_null返回空列表() {
        List<String> result = newAgent().findBlacklistWords(null);
        assertTrue(result.isEmpty());
    }

    // ─── getBlacklist() 测试 ────────────────────────────────────────

    // ─── M-12: AI高频比喻词测试 ─────────────────────────────────────

    @Test
    void filter_替换如同为像() {
        String result = newAgent().filter("灯火如同星辰。");
        assertFalse(result.contains("如同"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换恰似为像() {
        String result = newAgent().filter("白雪恰似银子。");
        assertFalse(result.contains("恰似"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换犹如为像() {
        String result = newAgent().filter("水面犹如镜面。");
        assertFalse(result.contains("犹如"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换恰如为像() {
        String result = newAgent().filter("其人恰如君子。");
        assertFalse(result.contains("恰如"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换一如既往为照旧() {
        String result = newAgent().filter("他一如既往沉默寡言。");
        assertFalse(result.contains("一如既往"));
        assertTrue(result.contains("照旧"));
    }

    @Test
    void filter_替换一如为跟从前一样() {
        String result = newAgent().filter("他一如昨日。");
        assertFalse(result.contains("一如"));
        assertTrue(result.contains("跟从前一样"));
    }

    @Test
    void filter_替换仿若为像() {
        String result = newAgent().filter("幻境仿若隔世。");
        assertFalse(result.contains("仿若"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_替换宛若为像() {
        String result = newAgent().filter("身形宛若游龙。");
        assertFalse(result.contains("宛若"));
        assertTrue(result.contains("像"));
    }

    @Test
    void filter_M12多词共存全部处理() {
        String input = "灯火如同星辰，恰似明月，宛若水镜，仿若隔世，犹如此生，一如往昔。";
        String result = newAgent().filter(input);
        assertFalse(result.contains("如同"));
        assertFalse(result.contains("恰似"));
        assertFalse(result.contains("宛若"));
        assertFalse(result.contains("仿若"));
        assertFalse(result.contains("犹如"));
        assertFalse(result.contains("一如"));
        assertTrue(result.contains("像"));
    }

    @Test
    void getBlacklist_返回17个词() {
        List<String> blacklist = newAgent().getBlacklist();
        assertEquals(17, blacklist.size());
        assertTrue(blacklist.contains("宛如"));
        assertTrue(blacklist.contains("仿佛"));
        assertTrue(blacklist.contains("如同"));
        assertTrue(blacklist.contains("恰似"));
        assertTrue(blacklist.contains("宛若"));
        assertTrue(blacklist.contains("仿若"));
        assertTrue(blacklist.contains("犹如"));
        assertTrue(blacklist.contains("恰如"));
        assertTrue(blacklist.contains("一如既往"));
        assertTrue(blacklist.contains("一如"));
        assertTrue(blacklist.contains("无法言说"));
        assertTrue(blacklist.contains("不禁"));
        assertTrue(blacklist.contains("缓缓说道"));
        assertTrue(blacklist.contains("轻声说道"));
        assertTrue(blacklist.contains("目光中满是"));
        assertTrue(blacklist.contains("心中一动"));
        assertTrue(blacklist.contains("似乎在诉说"));
    }
}
