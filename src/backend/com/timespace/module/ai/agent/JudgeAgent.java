package com.timespace.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.ai.service.AiPromptTemplateService;
import com.timespace.module.ai.util.AiPhraseFilter;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.entity.StoryCharacter;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 判官 Agent
 *
 * 职责：
 * 1. 评估用户选择的后果
 * 2. 计算历史偏离度变化
 * 3. 更新配角命运值
 * 4. 计算关键词共鸣度
 * 5. 生成涟漪效果
 *
 * Prompt 设计要点：
 * - 客观公正的审判者口吻
 * - 每次选择都有代价
 * - 道德两难，无完美解
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeAgent {

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;
    private final AiPhraseFilter aiPhraseFilter;
    private final AiPromptTemplateService promptTemplateService;

    // AI-07: 默认评估系统 Prompt（fallback 用）
    private static final String DEFAULT_EVALUATION_SYSTEM_PROMPT = """
            你是一位公正的判官，负责评估故事中每个选择的后果。

            当前故事状态：
            - 历史偏离度：%d/100（0=完全遵从历史，100=完全偏离历史）
            - 指定关键词：%s
            - 配角命运：%s

            评估原则：
            1. 每个选择都有代价，没有完美选项
            2. 命运与关键词共鸣：选择与关键词契合度高时，共鸣+1
            3. 历史偏离度：根据选择与真实历史的差异计算（0-15分/次）
            4. 配角命运：根据选择对配角命运的影响计算（-20 ~ +20）
            5. 生成涟漪效果：选择对世界造成的后续影响

            输出格式（JSON）：
            {
              "deviationChange": 5,          // 偏离度变化（正数=偏离，负数=回归）
              "newDeviation": 55,           // 新的偏离度
              "characterFateChanges": {     // 配角命运变化
                "配角名1": {"change": -10, "newValue": 40, "reason": "因主角选择而受难"},
                "配角名2": {"change": 5, "newValue": 55, "reason": "借势崛起"}
              },
              "keywordResonance": {          // 关键词共鸣
                "铜锁": 1,                   // 共鸣次数+1
                "旧伞": 0
              },
              "ripples": [                  // 涟漪效果
                {"target": "铜锁", "status": "锁芯渐凉"},
                {"target": "旧伞", "status": "被雨打湿"}
              ],
              "judgment": "此选择虽保全了XX，却牺牲了XX，命运的齿轮由此转向。"  // 判词
            }
            """;

    // AI-07: 运行时 Prompt 模板（从 DB 加载）
    private String evaluationSystemPromptTemplate;

    // P-01: 入局判词默认 Prompt
    private static final String DEFAULT_VERDICT_SYSTEM_PROMPT = """
            你是一位古代判官，擅长用古文写判词。判词应简洁有力，半文半白，20字以内。
            """;

    private String verdictSystemPromptTemplate;

    /**
     * P-01 生成入局判词
     * 用户选定 3 张关键词卡 + 1 张历史事件卡后，在入局前 AI 生成一段极简判词，暗示故事走向。
     *
     * @param keywords 3张关键词卡名称列表
     * @param eventTitle 历史事件标题
     * @return 判词结果（包含判词 + 原始输入）
     */
    public VerdictResult generateVerdict(List<String> keywords, String eventTitle) {
        log.info("[P-01] 生成入局判词: keywords={}, eventTitle={}", keywords, eventTitle);

        String keywordText = keywords == null || keywords.isEmpty()
                ? "无" : String.join("、", keywords);
        String eventText = (eventTitle == null || eventTitle.isBlank())
                ? "无" : eventTitle;

        String userPrompt = String.format("""
                三张关键词：%s，历史事件：%s。
                请用一句判词（20字以内）暗示这组卡可能产生的故事走向，
                用判官口吻，半文半白。只返回一句判词，不要解释，不要引号，不要括号。
                """,
                keywordText, eventText);

        String systemPrompt = verdictSystemPromptTemplate != null
                ? verdictSystemPromptTemplate : DEFAULT_VERDICT_SYSTEM_PROMPT;

        try {
            String judgment = aiClient.callSync(systemPrompt, userPrompt);
            // 清洗
            judgment = judgment.trim()
                    .replaceAll("^[\"'「『\\[\\s]+", "")
                    .replaceAll("[\"'」』\\]\\s]+$", "");
            if (judgment.length() > 25) {
                judgment = judgment.substring(0, 25);
            }
            if (judgment.isBlank()) {
                judgment = "墨中藏命，缘起无形。";
            }
            log.info("[P-01] 判词生成成功: {}", judgment);
            return new VerdictResult(judgment, keywordText, eventText);
        } catch (Exception e) {
            log.warn("[P-01] 判词生成失败，降级为兜底文案: {}", e.getMessage());
            return new VerdictResult("墨中藏命，缘起无形。", keywordText, eventText);
        }
    }

    /**
     * P-01 判词结果
     *
     * @param verdict  判词文本
     * @param keywords 关键词列表原文
     * @param event    事件标题原文
     */
    public record VerdictResult(String verdict, String keywords, String event) {}

    /**
     * 评估用户选择
     *
     * @param story 故事元信息
     * @param chapter 当前章节
     * @param selectedOption 用户选择的选项
     * @param keywords 关键词卡列表
     * @param characters 配角列表
     * @return 评估结果
     */
    public EvaluationResult evaluate(Story story, StoryChapter chapter,
                                     StoryChapter.Option selectedOption,
                                     List<KeywordCard> keywords,
                                     List<StoryCharacter> characters) {
        log.info("判官评估选择: storyId={}, chapterNo={}, optionId={}",
                story.getId(), chapter.getChapterNo(), selectedOption.getId());

        String systemPrompt = buildEvaluationSystemPrompt(story, keywords, characters);
        String userMessage = buildEvaluationUserMessage(chapter, selectedOption, keywords, characters);

        String response = aiClient.callSync(systemPrompt, userMessage);

        return parseEvaluationResult(response, story, chapter, selectedOption, keywords, characters);
    }

    // AI-07: 启动时从数据库加载 Prompt 模板，失败时 fallback 到硬编码默认值
    @PostConstruct
    public void loadPromptsFromDatabase() {
        log.info("[AI-07] 判官 Agent 正在加载 Prompt 模板...");

        this.evaluationSystemPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_JUDGE,
                AiPromptTemplateService.PROMPT_EVALUATION_SYSTEM,
                DEFAULT_EVALUATION_SYSTEM_PROMPT
        );
        log.info("[AI-07] 评估系统 Prompt 加载完成, length={}", evaluationSystemPromptTemplate.length());

        this.verdictSystemPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_JUDGE,
                "verdict_system",
                DEFAULT_VERDICT_SYSTEM_PROMPT
        );
        log.info("[P-01] 入局判词 Prompt 加载完成, length={}", verdictSystemPromptTemplate.length());
    }

    private String buildEvaluationSystemPrompt(Story story,
                                               List<KeywordCard> keywords,
                                               List<StoryCharacter> characters) {
        String keywordsText = keywords.stream()
                .map(k -> k.getName() + "(墨香=" + k.getRarity() + ")")
                .collect(Collectors.joining("、"));
        String charactersText = characters.stream()
                .map(c -> c.getName() + "[命运值=" + c.getFateValue() + "]")
                .collect(Collectors.joining("；"));

        return String.format(evaluationSystemPromptTemplate,
                story.getHistoryDeviation(),
                keywordsText,
                charactersText
        );
    }

    private String buildEvaluationUserMessage(StoryChapter chapter,
                                              StoryChapter.Option selectedOption,
                                              List<KeywordCard> keywords,
                                              List<StoryCharacter> characters) {
        String keywordsText = keywords.stream()
                .map(KeywordCard::getName)
                .collect(Collectors.joining("、"));

        StringBuilder sb = new StringBuilder();
        sb.append("【场景回顾】\n").append(chapter.getSceneText()).append("\n\n");
        sb.append("【用户选择】\n").append(selectedOption.getId()).append(". ")
                .append(selectedOption.getText()).append("\n\n");
        sb.append("【选择暗示】\n").append(selectedOption.getHint()).append("\n\n");
        sb.append("【关键词】\n").append(keywordsText).append("\n\n");
        sb.append("【配角】\n").append(characters.stream()
                .map(c -> c.getName() + "=" + c.getFateValue())
                .collect(Collectors.joining("、"))).append("\n\n");
        sb.append("请评估此选择的后果，返回JSON。");

        return sb.toString();
    }

    private EvaluationResult parseEvaluationResult(String response, Story story,
                                                   StoryChapter chapter,
                                                   StoryChapter.Option selectedOption,
                                                   List<KeywordCard> keywords,
                                                   List<StoryCharacter> characters) {
        EvaluationResult result = new EvaluationResult();

        try {
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);

                // 偏离度
                Object deviationChangeObj = parsed.get("deviationChange");
                int deviationChange = deviationChangeObj instanceof Number
                        ? ((Number) deviationChangeObj).intValue() : 0;
                result.setDeviationChange(deviationChange);

                Object newDeviationObj = parsed.get("newDeviation");
                int newDeviation = newDeviationObj instanceof Number
                        ? ((Number) newDeviationObj).intValue() : story.getHistoryDeviation();
                result.setNewDeviation(Math.max(0, Math.min(100, newDeviation)));

                // 配角命运
                Map<String, Object> characterFateChanges =
                        (Map<String, Object>) parsed.get("characterFateChanges");
                if (characterFateChanges != null) {
                    for (Map.Entry<String, Object> entry : characterFateChanges.entrySet()) {
                        String charName = entry.getKey();
                        Map<String, Object> change = (Map<String, Object>) entry.getValue();
                        CharacterFateChange cfc = new CharacterFateChange();
                        cfc.setName(charName);
                        cfc.setChange(((Number) change.get("change")).intValue());
                        cfc.setNewValue(((Number) change.get("newValue")).intValue());
                        cfc.setReason((String) change.get("reason"));
                        result.getCharacterFateChanges().put(charName, cfc);
                    }
                }

                // 关键词共鸣
                Map<String, Object> keywordResonance = (Map<String, Object>) parsed.get("keywordResonance");
                if (keywordResonance != null) {
                    for (Map.Entry<String, Object> entry : keywordResonance.entrySet()) {
                        String keywordName = entry.getKey();
                        int resonance = ((Number) entry.getValue()).intValue();
                        // 找出对应的cardId
                        for (KeywordCard k : keywords) {
                            if (k.getName().equals(keywordName)) {
                                result.getKeywordResonance().put(k.getId(), resonance);
                            }
                        }
                    }
                }

                // 涟漪效果
                List<Map<String, String>> ripples = (List<Map<String, String>>) parsed.get("ripples");
                if (ripples != null) {
                    for (Map<String, String> ripple : ripples) {
                        StoryChapter.RippleEffect effect = new StoryChapter.RippleEffect();
                        effect.setTarget(ripple.get("target"));
                        effect.setStatus(ripple.get("status"));
                        result.getRipples().add(effect);
                    }
                }

                result.setJudgment(aiPhraseFilter.filter((String) parsed.get("judgment")));
            }
        } catch (Exception e) {
            log.error("解析判官评估结果失败: {}", e.getMessage());
            // 使用默认值
            result.setDeviationChange(5);
            result.setNewDeviation(Math.min(100, story.getHistoryDeviation() + 5));
        }

        return result;
    }

    @Data
    public static class EvaluationResult {
        private int deviationChange;
        private int newDeviation;
        private Map<String, CharacterFateChange> characterFateChanges = new java.util.HashMap<>();
        private Map<Long, Integer> keywordResonance = new java.util.HashMap<>(); // cardId -> 共鸣次数
        private List<StoryChapter.RippleEffect> ripples = new java.util.ArrayList<>();
        private String judgment;
    }

    @Data
    public static class CharacterFateChange {
        private String name;
        private int change;
        private int newValue;
        private String reason;
    }
}
