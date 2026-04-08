package com.timespace.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.entity.StoryCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 说书人 Agent
 *
 * 职责：
 * 1. 生成故事场景描写（逐章节）
 * 2. 在故事结束时生成完整手稿正文
 * 3. 融入关键词（关键词共鸣机制）
 * 4. 控制叙事风格（白描/江湖/笔记/话本）
 *
 * Prompt 设计要点：
 * - 温和中年说书人口吻
 * - 禁止"宛如""仿佛""无法言说"等AI腔
 * - 场景描写控制在200字左右
 * - 必须融入指定关键词
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorytellerAgent {

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;

    /**
     * 生成单章节场景
     *
     * @param story 故事元信息
     * @param chapterNo 章节号
     * @param lastChoice 上一次选择
     * @param keywords 关键词卡列表
     * @param characters 配角列表
     * @param onChunk 流式回调（WebSocket推送）
     * @return 生成的章节内容
     */
    public StoryChapter generateChapter(Story story, int chapterNo, StoryChapter.Option lastChoice,
                                        List<KeywordCard> keywords, List<StoryCharacter> characters,
                                        java.util.function.Consumer<String> onChunk) {
        log.info("说书人生成章节: storyId={}, chapterNo={}", story.getId(), chapterNo);

        String systemPrompt = buildChapterSystemPrompt(story, chapterNo, keywords, characters);
        String userMessage = buildChapterUserMessage(story, chapterNo, lastChoice, keywords);

        String response = aiClient.callStream(systemPrompt, userMessage, chunk -> {
            if (onChunk != null) onChunk.accept(chunk);
        });

        return parseChapterResponse(response, chapterNo);
    }

    /**
     * 生成完整手稿
     * 在故事结束后调用，将所有章节合并并润色
     */
    public String generateManuscript(Story story, List<StoryChapter> chapters,
                                     List<KeywordCard> keywords, List<StoryCharacter> characters) {
        log.info("说书人生成手稿: storyId={}, chapters={}", story.getId(), chapters.size());

        String systemPrompt = buildManuscriptSystemPrompt(story, keywords, characters);
        String userMessage = buildManuscriptUserMessage(story, chapters);

        return aiClient.callSync(systemPrompt, userMessage);
    }

    private String buildChapterSystemPrompt(Story story, int chapterNo, List<KeywordCard> keywords,
                                           List<StoryCharacter> characters) {
        String styleName = getStyleName(story.getStyle());
        String keywordsText = keywords.stream()
                .map(k -> k.getName() + "(" + getCategoryName(k.getCategory()) + ")")
                .collect(Collectors.joining("、"));
        String charactersText = characters.stream()
                .map(c -> c.getName() + "[" + getCharacterTypeName(c.getCharacterType()) + "，命运值=" + c.getFateValue() + "]")
                .collect(Collectors.joining("；"));

        return String.format("""
                你是一位温和的中年说书人，擅长用%s风格叙事。

                当前故事信息：
                - 章节：第%d章
                - 时代背景：%s
                - 叙事风格：%s
                - 主角身份：%s
                - 指定关键词：%s（请自然融入叙事）
                - 登场配角：%s

                入局三问答案：
                %s

                写作要求：
                1. 场景描写200字左右，情感细腻但不煽情
                2. 禁止使用"宛如""仿佛""宛若""恰似""若隐若现""无法言说"等词汇
                3. 关键词必须自然融入场景描写中
                4. 每段话不超过3句
                5. 结尾必须给出一个两难选择，让读者有代入感
                6. 最后用JSON格式返回章节内容，格式如下：
                {
                  "sceneText": "场景描写正文...",
                  "options": [
                    {"id": 1, "text": "选项1文字", "hint": "隐藏提示：选择后可能的后果"},
                    {"id": 2, "text": "选项2文字", "hint": "隐藏提示：选择后可能的后果"},
                    {"id": 3, "text": "选项3文字", "hint": "隐藏提示：选择后可能的后果"}
                  ]
                }
                """,
                styleName, chapterNo, "历史时代", styleName,
                getIdentityDesc(story.getIdentityType()),
                keywordsText, charactersText,
                story.getEntryAnswers()
        );
    }

    private String buildChapterUserMessage(Story story, int chapterNo, StoryChapter.Option lastChoice,
                                           List<KeywordCard> keywords) {
        if (lastChoice == null) {
            return String.format("请生成第%d章的开篇场景和选项。", chapterNo);
        }
        return String.format("""
                上文情节：主角选择了「%s」

                请继续第%d章的叙事：
                1. 描写该选择带来的直接后果（100字）
                2. 引入新的情境或冲突
                3. 给出三个新的两难选项
                4. 自然融入关键词

                请用JSON格式返回。
                """, lastChoice.getText(), chapterNo);
    }

    private String buildManuscriptSystemPrompt(Story story, List<KeywordCard> keywords,
                                               List<StoryCharacter> characters) {
        return String.format("""
                你是一位资深文学编辑，擅长润色和整合故事文本。

                背景：
                - 原稿：多章节故事文本
                - 关键词：%s（必须全部融入正文）
                - 叙事风格：%s
                - 目标字数：3000-8000字

                任务：
                1. 整合所有章节，去除重复和矛盾
                2. 润色文字，保持%s风格
                3. 确保所有关键词自然融入
                4. 添加适当的章节过渡
                5. 输出完整的短篇小说正文（不含选项和提示）

                特别注意：
                - 禁止"宛如""仿佛""无法言说"等AI腔
                - 结局要有情感力量
                - 字数控制在3000-8000字
                """,
                keywords.stream().map(KeywordCard::getName).collect(Collectors.joining("、")),
                getStyleName(story.getStyle()),
                getStyleName(story.getStyle())
        );
    }

    private String buildManuscriptUserMessage(Story story, List<StoryChapter> chapters) {
        StringBuilder sb = new StringBuilder("请整合以下章节内容：\n\n");
        for (StoryChapter chapter : chapters) {
            sb.append("【第").append(chapter.getChapterNo()).append("章】\n");
            sb.append(chapter.getSceneText()).append("\n");
            sb.append("用户选择：").append(chapter.getSelectedOption()).append("\n\n");
        }
        return sb.toString();
    }

    private StoryChapter parseChapterResponse(String response, int chapterNo) {
        StoryChapter chapter = new StoryChapter();
        chapter.setChapterNo(chapterNo);

        try {
            // 尝试从JSON中提取
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
                chapter.setSceneText((String) parsed.get("sceneText"));

                List<Map<String, Object>> optionsRaw = (List<Map<String, Object>>) parsed.get("options");
                if (optionsRaw != null) {
                    List<StoryChapter.Option> options = new ArrayList<>();
                    for (int i = 0; i < optionsRaw.size(); i++) {
                        Map<String, Object> opt = optionsRaw.get(i);
                        StoryChapter.Option option = new StoryChapter.Option();
                        option.setId((Integer) opt.getOrDefault("id", i + 1));
                        option.setText((String) opt.get("text"));
                        option.setHint((String) opt.get("hint"));
                        options.add(option);
                    }
                    chapter.setOptions(options);
                }
            } else {
                // 非JSON格式，整段作为场景描写
                chapter.setSceneText(response);
            }
        } catch (Exception e) {
            log.error("解析章节响应失败: {}", e.getMessage());
            chapter.setSceneText(response);
        }

        return chapter;
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

    private String getCategoryName(Integer category) {
        if (category == null) return "器物";
        return switch (category) {
            case 1 -> "器物";
            case 2 -> "职人";
            case 3 -> "风物";
            case 4 -> "情绪";
            case 5 -> "称谓";
            default -> "器物";
        };
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

    private String getIdentityDesc(Integer identityType) {
        if (identityType == null) return "历史过客";
        return switch (identityType) {
            case 1 -> "历史高位者（帝王、将相、名士）";
            case 2 -> "历史低位者（平民、仆役、流民）";
            case 3 -> "旁观者（记录者、旅人）";
            default -> "历史过客";
        };
    }
}
