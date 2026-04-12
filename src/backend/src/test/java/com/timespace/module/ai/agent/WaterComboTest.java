package com.timespace.module.ai.agent;

import com.timespace.module.card.entity.KeywordCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P-04 三水意象彩蛋测试
 *
 * 触发条件：入局时恰好 3 张关键词卡都含水意象
 * 预期行为：故事首章 prompt 中注入【彩蛋触发：三水意象——故事中必须出现至少一场雨】
 */
@DisplayName("P-04 三水意象彩蛋测试")
@Tag("water-combo")
class WaterComboTest {

    /**
     * 通过反射调用 private 方法 isThreeWaterImagery
     */
    private boolean invokeIsThreeWaterImagery(List<KeywordCard> keywords) throws Exception {
        StorytellerAgent agent = new StorytellerAgent(null, null, null, null);
        Method method = StorytellerAgent.class.getDeclaredMethod("isThreeWaterImagery", List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(agent, keywords);
    }

    // ── 触发彩蛋：3 张水意象 ────────────────────────────────────────

    @Test
    @DisplayName("3张水意象卡（雨、江、河）应触发彩蛋")
    void testThreeWaterCardsTrigger() throws Exception {
        KeywordCard k1 = card("K01001", "春雨", "雨", 1);
        KeywordCard k2 = card("K01002", "江声", "江", 1);
        KeywordCard k3 = card("K01003", "渔火", "河", 1);

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("3张水意象卡（湖、海、泪）应触发彩蛋")
    void testThreeWaterCardsLakeSeaTears() throws Exception {
        KeywordCard k1 = card("K01001", "洞庭", "湖", 1);
        KeywordCard k2 = card("K01002", "海潮", "海", 1);
        KeywordCard k3 = card("K01003", "旧梦", "泪", 1);

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("3张水意象卡（露、霜、雾）应触发彩蛋")
    void testThreeWaterCardsDewFrostFog() throws Exception {
        KeywordCard k1 = card("K01001", "朝露", "露", 1);
        KeywordCard k2 = card("K01002", "寒霜", "霜", 1);
        KeywordCard k3 = card("K01003", "夜雾", "雾", 1);

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("tag含水意象词（船、帆、涛）应触发彩蛋")
    void testWaterByTag() throws Exception {
        KeywordCard k1 = card("K01001", "孤帆", "船", 1);
        KeywordCard k2 = card("K01002", "远影", "帆", 1);
        KeywordCard k3 = card("K01003", "浪花", "涛", 1);

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("name含水意象词（名字含水字）应触发彩蛋")
    void testWaterByName() throws Exception {
        KeywordCard k1 = new KeywordCard().setName("雨巷").setTag("");
        KeywordCard k2 = new KeywordCard().setName("河流").setTag("");
        KeywordCard k3 = new KeywordCard().setName("海风").setTag("");

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    // ── 不触发彩蛋：不足3张水意象 ─────────────────────────────────

    @Test
    @DisplayName("只有2张水意象卡不应触发彩蛋")
    void testOnlyTwoWaterCardsNoTrigger() throws Exception {
        KeywordCard k1 = card("K01001", "春雨", "雨", 1);
        KeywordCard k2 = card("K01002", "江声", "江", 1);
        KeywordCard k3 = card("K01003", "孤灯", "火", 1);

        assertFalse(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("只有1张水意象卡不应触发彩蛋")
    void testOnlyOneWaterCardNoTrigger() throws Exception {
        KeywordCard k1 = card("K01001", "春雨", "雨", 1);
        KeywordCard k2 = card("K01002", "古道", "路", 1);
        KeywordCard k3 = card("K01003", "瘦马", "马", 1);

        assertFalse(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    @Test
    @DisplayName("0张水意象卡不应触发彩蛋")
    void testNoWaterCardsNoTrigger() throws Exception {
        KeywordCard k1 = card("K01001", "古道", "路", 1);
        KeywordCard k2 = card("K01002", "西风", "风", 1);
        KeywordCard k3 = card("K01003", "瘦马", "马", 1);

        assertFalse(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    // ── 边界条件 ─────────────────────────────────────────────────

    @Test
    @DisplayName("不足3张关键词卡不应触发彩蛋")
    void testLessThanThreeCardsNoTrigger() throws Exception {
        KeywordCard k1 = card("K01001", "春雨", "雨", 1);
        KeywordCard k2 = card("K01002", "江声", "江", 1);

        assertFalse(invokeIsThreeWaterImagery(List.of(k1, k2)));
    }

    @Test
    @DisplayName("null 关键词列表不应触发彩蛋")
    void testNullListNoTrigger() throws Exception {
        assertFalse(invokeIsThreeWaterImagery(null));
    }

    @Test
    @DisplayName("空关键词列表不应触发彩蛋")
    void testEmptyListNoTrigger() throws Exception {
        assertFalse(invokeIsThreeWaterImagery(List.of()));
    }

    @Test
    @DisplayName("超过3张卡时只取前3张判断")
    void testMoreThanThreeCardsTakeFirstThree() throws Exception {
        // 前3张都有水意象，第4张没有 → 应该触发
        KeywordCard k1 = card("K01001", "春雨", "雨", 1);
        KeywordCard k2 = card("K01002", "江声", "江", 1);
        KeywordCard k3 = card("K01003", "湖光", "湖", 1);
        KeywordCard k4 = card("K04001", "古道", "路", 1);

        assertTrue(invokeIsThreeWaterImagery(List.of(k1, k2, k3, k4)));
    }

    @Test
    @DisplayName("tag和name都无水意象时不应触发")
    void testBothTagAndNameNoWater() throws Exception {
        KeywordCard k1 = new KeywordCard().setName("青山").setTag("山");
        KeywordCard k2 = new KeywordCard().setName("古道").setTag("路");
        KeywordCard k3 = new KeywordCard().setName("西风").setTag("风");

        assertFalse(invokeIsThreeWaterImagery(List.of(k1, k2, k3)));
    }

    // ── Helper ────────────────────────────────────────────────────

    private KeywordCard card(String cardNo, String name, String tag, int category) {
        return new KeywordCard()
                .setCardNo(cardNo)
                .setName(name)
                .setTag(tag)
                .setCategory(category);
    }
}
