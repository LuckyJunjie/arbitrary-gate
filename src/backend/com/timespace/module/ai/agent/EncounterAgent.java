package com.timespace.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryCharacter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * S-14 偶遇 Agent
 *
 * 职责：
 * 在章节转场时，以30%概率触发配角偶遇事件
 * 生成50-80字的偶遇场景 + 2个选项（搭话/装没看见）
 *
 * 偶遇选项影响 fate_value：
 * - 搭话(A)：命运值 +10
 * - 装没看见(B)：命运值 -5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncounterAgent {

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一位古代说书人，正在讲述一个历史互动故事。

            场景：故事章节之间的转场间隙，配角偶遇片段。
            要求：
            - 生成50-80字的偶遇场景，描写主角在旅途中意外遇到一位配角的情景
            - 场景要有画面感，如同电影中的转场片段
            - 必须提供两个选项：
              A. 主动搭话
              B. 装作没看见，继续赶路
            - 口吻：说书人轻快叙事，如同"却说那日……"

            返回格式（严格JSON）：
            {
              "encounterText": "却说那日主角行至城门口，忽见一道熟悉身影……",
              "optionA": "上前搭话",
              "optionB": "装作没看见"
            }
            """;

    /**
     * 生成偶遇场景
     *
     * @param story 故事信息
     * @param chapterNo 当前章节号（偶遇后将进入 chapterNo+1）
     * @param characters 当前已出场的配角列表
     * @return 偶遇结果，包含场景文字和两个选项
     */
    public EncounterResult generateEncounter(Story story, int chapterNo, List<StoryCharacter> characters) {
        log.info("[S-14] 生成偶遇场景: storyId={}, chapterNo={}, characters={}",
                story.getId(), chapterNo, characters.size());

        String characterNames = characters.stream()
                .map(c -> c.getName() + "(命运值=" + c.getFateValue() + ")")
                .reduce((a, b) -> a + "、" + b)
                .orElse("无");

        String userMessage = String.format("""
                当前故事信息：
                - 时代：%s
                - 当前章节：第%d章
                - 已出场配角：%s

                请生成一段50-80字的配角偶遇场景。
                """,
                "历史时代",
                chapterNo,
                characterNames
        );

        try {
            String response = aiClient.callSync(SYSTEM_PROMPT, userMessage);
            return parseEncounterResponse(response);
        } catch (Exception e) {
            log.warn("[S-14] 偶遇生成失败，使用兜底: error={}", e.getMessage());
            return getFallbackEncounter();
        }
    }

    private EncounterResult parseEncounterResponse(String response) {
        try {
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
                String encounterText = (String) parsed.get("encounterText");
                String optionA = (String) parsed.getOrDefault("optionA", "上前搭话");
                String optionB = (String) parsed.getOrDefault("optionB", "装作没看见");

                // 验证字数
                if (encounterText != null && encounterText.length() > 100) {
                    encounterText = encounterText.substring(0, 100);
                }

                return new EncounterResult(encounterText, optionA, optionB);
            }
        } catch (Exception e) {
            log.warn("[S-14] 解析偶遇响应失败: {}, raw={}", e.getMessage(), response);
        }
        return getFallbackEncounter();
    }

    private EncounterResult getFallbackEncounter() {
        return new EncounterResult(
                "却说那日主角行至渡口，见一老翁独坐船头，似在等什么人。",
                "上前搭话",
                "装作没看见"
        );
    }

    @Data
    public static class EncounterResult {
        /** 偶遇场景描述（50-80字） */
        private final String encounterText;
        /** 选项A：搭话 */
        private final String optionA;
        /** 选项B：装没看见 */
        private final String optionB;

        /** 搭话的命运值变化 */
        public int getFateChangeA() { return 10; }
        /** 装没看见的命运值变化 */
        public int getFateChangeB() { return -5; }
    }
}
