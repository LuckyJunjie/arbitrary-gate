package com.timespace.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryCharacter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 稗官 Agent
 *
 * 职责：
 * 1. 生成配角的后日谈
 * 2. 野史口吻评述故事与真实历史的异同
 * 3. 生成最终的朱批（批注）
 *
 * Prompt 设计要点：
 * - 稗官是民间野史记录者
 * - 口吻：市井茶馆说故事的感觉
 * - 以"稗官曰"开头
 * - 200字左右
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiguanAgent {

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;

    /**
     * 生成后日谈（所有配角）
     */
    public List<StoryCharacter> generateEpilogues(Story story, List<StoryCharacter> characters,
                                                    int finalDeviation) {
        log.info("稗官生成后日谈: storyId={}, characters={}", story.getId(), characters.size());

        for (StoryCharacter character : characters) {
            String epilogue = generateSingleEpilogue(story, character, finalDeviation);
            character.setFinalEpilogue(epilogue);
        }

        return characters;
    }

    /**
     * 生成单条后日谈
     */
    public String generateSingleEpilogue(Story story, StoryCharacter character, int finalDeviation) {
        String systemPrompt = String.format("""
                你是一位民间稗官，专门记录野史轶闻。

                口吻要求：
                - 以"稗官曰"开头
                - 市井茶馆说故事的口吻
                - 可以有民间传说的夸张色彩
                - 不必拘泥于正史
                - 200字左右

                特别注意：
                - 不得使用"宛如""仿佛""无法言说"等文绉绉词汇
                - 语言要接地气，像老茶馆里讲故事
                """);

        String userMessage = String.format("""
                故事主角使用关键词卡经历了%d章节的冒险。
                最终历史偏离度：%d/100（0=完全遵从历史，100=完全偏离）

                请为以下配角撰写一段后日谈（野史风格，200字）：
                配角名：%s
                角色类型：%s
                最终命运值：%d/100
                与主角关系：%s

                以"稗官曰"开头，写一段有趣的野史传闻。
                """,
                story.getCurrentChapter(),
                finalDeviation,
                character.getName(),
                getCharacterTypeName(character.getCharacterType()),
                character.getFateValue(),
                character.getRelationToUser()
        );

        return aiClient.callSync(systemPrompt, userMessage);
    }

    /**
     * 生成朱批（文学批注）
     */
    public String generateAnnotation(int chapterNo, String chapterText) {
        String systemPrompt = String.format("""
                你是一位古典文学批注家，擅长在原文旁添加精妙的朱砂批注。

                风格：
                - 类似金圣叹批《水浒》、脂砚斋批《红楼梦》
                - 简短有力，点到即止
                - 用朱砂红色标注
                - 可以点评用词、情节、结构

                格式：
                - 单条批注不超过20字
                - 标注在关键句旁
                - 通常1-3条批注即可
                """);

        String userMessage = String.format("请为第%d章的以下内容写批注（1-3条）：\n\n%s",
                chapterNo, chapterText);

        return aiClient.callSync(systemPrompt, userMessage);
    }

    /**
     * 生成稗官总评
     */
    public String generateOverallComment(Story story, int totalWords, int finalDeviation) {
        String systemPrompt = String.format("""
                你是一位民间稗官，在故事结尾以野史口吻写一段总评。

                要求：
                - 以"稗官曰"开头
                - 100字左右
                - 点评主角旅程的得失
                - 对比真实历史
                - 可以有传奇色彩

                禁止：
                - "宛如""仿佛""无法言说"
                - 过于文绉绉的表达
                """);

        String userMessage = String.format("""
                故事基本信息：
                - 总字数：%d
                - 最终偏离度：%d/100
                - 叙事风格：%s
                - 结局类型：%s

                请以稗官口吻写一段总结性评语。
                """,
                totalWords,
                finalDeviation,
                getStyleName(story.getStyle()),
                finalDeviation < 30 ? "回归历史正道" :
                        finalDeviation < 70 ? "在历史与虚构间徘徊" : "完全改写历史"
        );

        return aiClient.callSync(systemPrompt, userMessage);
    }

    private String getCharacterTypeName(Integer type) {
        if (type == null) return "命运羁绊";
        return switch (type) {
            case 1 -> "命运羁绊";
            case 2 -> "历史节点";
            case 3 -> "市井过客";
            default -> "命运羁绊";
        };
    }

    private String getStyleName(Integer style) {
        if (style == null) return "白描";
        return switch (style) {
            case 1 -> "白描";
            case 2 -> "江湖";
            case 3 -> "笔记";
            case 4 -> "话本";
            default -> "白描";
        };
    }
}
