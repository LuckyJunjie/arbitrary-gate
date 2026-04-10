package com.timespace.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.module.ai.client.AIClient;
import com.timespace.module.ai.service.AIGatewayService.ManuscriptResult;
import com.timespace.module.ai.service.AiPromptTemplateService;
import com.timespace.module.card.entity.KeywordCard;
import com.timespace.module.story.entity.Story;
import com.timespace.module.story.entity.StoryChapter;
import com.timespace.module.story.entity.StoryCharacter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import com.timespace.module.ai.util.AiPhraseFilter;

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
 *
 * AI-07 Prompt 热更新：
 * - 启动时从数据库加载 prompt 模板
 * - 失败时 fallback 到硬编码默认值
 * - 支持运行时更新 prompt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorytellerAgent {

    private final AIClient aiClient;
    private final ObjectMapper objectMapper;
    private final AiPromptTemplateService promptTemplateService;
    private final AiPhraseFilter aiPhraseFilter;

    // 运行时 prompt 模板（从 DB 加载）
    private String chapterSystemPromptTemplate;
    private String manuscriptSystemPromptTemplate;
    private String inscriptionPromptTemplate;

    // 默认 Prompt 常量（fallback 用）
    private static final String DEFAULT_CHAPTER_SYSTEM_PROMPT = """
            你是一位擅长历史叙事的古代说书人。你的声音沉稳有力，讲述故事时如同亲历。

            叙事风格：
            - 豪放：大气磅礴，场面恢弘，笔触粗犷
            - 婉约：细腻入微，情感丰富，笔触柔和
            - 诗意：意境深远，语言优美，富有诗画感
            - 史实：严谨考究，尊重史实，客观叙述

            关键词必须全部融入正文。每个关键词出现至少1次。
            禁止"宛如""仿佛""无法言说""不禁""缓缓说道""轻声说道""目光中满是""心中一动""似乎在诉说"等AI腔词汇。
            """;

    private static final String DEFAULT_MANUSCRIPT_SYSTEM_PROMPT = """
            你是一位资深文学编辑，擅长润色和整合故事文本。

            背景：
            - 原稿：多章节故事文本
            - 关键词：{keywords}（必须全部融入正文）
            - 叙事风格：{style}
            - 目标字数：3000-8000字

            任务：
            1. 整合所有章节，去除重复和矛盾
            2. 润色文字，保持{style}风格
            3. 确保所有关键词自然融入
            4. 添加适当的章节过渡
            5. 生成3个备选标题（古典文学风格，各不超过10字）
            6. 输出完整的短篇小说正文（不含选项和提示）

            特别注意：
            - 禁止"宛如""仿佛""无法言说"等AI腔
            - 结局要有情感力量
            - 字数控制在3000-8000字
            - 标题要典雅、有意境
            """;

    private static final String DEFAULT_INCRIPTION_PROMPT = """
            你是一个古代文士。请为下面的故事生成一句题记（20-40字）。
            风格要求：
            - 古典、含蓄、有画面感
            - 暗示故事主题但不说破
            - 如同诗词的起句，留有余韵
            - 使用文言文风格的措辞，但不必完全复古
            - 禁止使用"宛如""仿佛""无法言说"等AI腔词汇

            只返回题记，不要其他内容。
            """;

    /**
     * AI-07: 启动时从数据库加载 prompt 模板
     * 失败时 fallback 到硬编码默认值
     */
    @PostConstruct
    public void loadPromptsFromDatabase() {
        log.info("[AI-07] 说书人 Agent 正在加载 Prompt 模板...");

        this.chapterSystemPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_STORYTELLER,
                AiPromptTemplateService.PROMPT_CHAPTER_SYSTEM,
                DEFAULT_CHAPTER_SYSTEM_PROMPT
        );
        log.info("[AI-07] 章节系统 Prompt 加载完成, length={}", chapterSystemPromptTemplate.length());

        this.manuscriptSystemPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_STORYTELLER,
                AiPromptTemplateService.PROMPT_MANUSCRIPT_SYSTEM,
                DEFAULT_MANUSCRIPT_SYSTEM_PROMPT
        );
        log.info("[AI-07] 手稿系统 Prompt 加载完成, length={}", manuscriptSystemPromptTemplate.length());

        this.inscriptionPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_STORYTELLER,
                AiPromptTemplateService.PROMPT_INSCRIPTION,
                DEFAULT_INCRIPTION_PROMPT
        );
        log.info("[AI-07] 题记 Prompt 加载完成, length={}", inscriptionPromptTemplate.length());
    }

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
                                        String gestureIntensity,
                                        java.util.function.Consumer<String> onChunk) {
        log.info("说书人生成章节: storyId={}, chapterNo={}, gestureIntensity={}", story.getId(), chapterNo, gestureIntensity);

        String systemPrompt = buildChapterSystemPrompt(story, chapterNo, keywords, characters, gestureIntensity);
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

    /**
     * 生成完整手稿（含3个备选标题和题记）
     * 在故事结束时调用，返回手稿正文、3个备选标题和散文诗题记
     */
    public AIGatewayService.ManuscriptResult generateManuscriptWithTitles(Story story,
                                                                          List<StoryChapter> chapters,
                                                                          List<KeywordCard> keywords,
                                                                          List<StoryCharacter> characters) {
        return generateManuscriptWithTitles(story, chapters, keywords, characters, "");
    }

    /**
     * 生成完整手稿（含3个备选标题和题记），支持关键词融入强调
     *
     * @param story           故事元信息
     * @param chapters        所有章节
     * @param keywords        关键词卡列表
     * @param characters      配角列表
     * @param emphasisPrompt  关键词融入强调 prompt（由 KeywordChecker 生成）
     */
    public AIGatewayService.ManuscriptResult generateManuscriptWithTitles(Story story,
                                                                          List<StoryChapter> chapters,
                                                                          List<KeywordCard> keywords,
                                                                          List<StoryCharacter> characters,
                                                                          String emphasisPrompt) {
        log.info("说书人生成手稿（含备选标题）: storyId={}, chapters={}, hasEmphasis={}",
                story.getId(), chapters.size(), emphasisPrompt != null && !emphasisPrompt.isBlank());

        String systemPrompt = buildManuscriptSystemPromptWithTitles(story, keywords, characters, emphasisPrompt);
        String userMessage = buildManuscriptUserMessage(story, chapters);

        String response = aiClient.callSync(systemPrompt, userMessage);
        AIGatewayService.ManuscriptResult result = parseManuscriptWithTitlesResponse(response);

        // 同步生成题记
        String inscription = generateInscription(story, keywords);
        return new AIGatewayService.ManuscriptResult(result.manuscriptText(), result.candidateTitles(), inscription);
    }

    /**
     * 生成散文诗式题记
     * 风格：古雅、留白、有意境；20-50字，呼应关键词/历史事件/故事主题
     * 与正文白描风格不同，题记更注重意境和留白
     */
    public String generateInscription(Story story, List<KeywordCard> keywords) {
        log.info("说书人生成题记: storyId={}", story.getId());

        String keywordsText = keywords.stream()
                .map(KeywordCard::getName)
                .collect(Collectors.joining("、"));

        // AI-07: 使用从 DB 加载的 prompt 模板
        String systemPrompt = String.format(inscriptionPromptTemplate,
                "历史时代",
                getStyleName(story.getStyle()),
                keywordsText.isEmpty() ? "无" : keywordsText
        );

        String userMessage = String.format("""
                请为以下故事生成一段散文诗风格的题记引子：

                关键词：%s
                时代背景：%s
                入局答案：%s

                要求：20-50字，古雅有意境，呼应主题。
                """,
                keywordsText.isEmpty() ? "无" : keywordsText,
                "历史时代",
                story.getEntryAnswers() != null ? story.getEntryAnswers() : "无"
        );

        try {
            String raw = aiClient.callSync(systemPrompt, userMessage);
            // 清理：去掉首尾空白和可能的引号
            String cleaned = raw.trim();
            if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
                (cleaned.startsWith("「") && cleaned.endsWith("」")) ||
                (cleaned.startsWith("《") && cleaned.endsWith("》"))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            if (cleaned.length() < 5) {
                return getDefaultInscription();
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("生成题记失败，使用默认题记: {}", e.getMessage());
            return getDefaultInscription();
        }
    }

    private String getDefaultInscription() {
        return "光阴如箭，岁月如梭，古今多少事，都付笑谈中。";
    }

    private String buildChapterSystemPrompt(Story story, int chapterNo, List<KeywordCard> keywords,
                                           List<StoryCharacter> characters, String gestureIntensity) {
        String styleName = getStyleName(story.getStyle());
        String keywordsText = keywords.stream()
                .map(k -> k.getName() + "(" + getCategoryName(k.getCategory()) + ")")
                .collect(Collectors.joining("、"));
        String charactersText = characters.stream()
                .map(c -> c.getName() + "[" + getCharacterTypeName(c.getCharacterType()) + "，命运值=" + c.getFateValue() + "]")
                .collect(Collectors.joining("；"));

        boolean isFirstChapter = chapterNo == 1;
        // E-07: 关键词落位 prompt（仅第一章需要返回）
        String keywordPositionSection = isFirstChapter ? """
                9. 【第一章专属】请为三个关键词各自标注角色归属（落位），从以下三种角色中选一个归属：
                   - 核心意象：该关键词作为故事的核心象征，贯穿始终
                   - 转折道具：该关键词在故事中将引发关键转折
                   - 人物关联：该关键词与某位配角命运紧密相连
                   每个关键词只能归属一种角色，三种角色都要出现。
                   例如：{"keywordPositions": [{"keyword": "旧船票", "role": "核心意象", "roleOwner": null}, {"keyword": "铜锁芯", "role": "转折道具", "roleOwner": null}, {"keyword": "摆渡人", "role": "人物关联", "roleOwner": "张翁"}]}
                """ : "";

        // P-04: 三水意象彩蛋检测
        // 水相关意象词：渡口/船/江/河/雨/水/舟/帆/潮/浪
        // 如果三张关键词卡的tag字段都包含水相关意象，则在prompt中注入"必须出现一场雨"
        String p04RainHint = isThreeWaterImagery(keywords)
                ? "\n\n【重要彩蛋】三张关键词卡均有水意象，故事中必须出现一场雨。"
                : "";

        // S-11: 手势轻重缓急影响叙事口吻
        String gestureGuidance = buildGestureGuidance(gestureIntensity);

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

                【M-11 文学风格差异化要求】
                %s

                【S-11 手势轻重缓急】
                %s

                写作要求：
                1. 场景描写200字左右，情感细腻但不煽情
                2. 禁止使用"宛如""仿佛""宛若""恰似""若隐若现""无法言说"等词汇
                3. 关键词必须自然融入场景描写中
                4. 每段话不超过3句
                5. 结尾必须给出一个两难选择，让读者有代入感
                6. 每个配角出场时，不要用上帝视角介绍身份。用主角的眼睛，描述此人"此刻的样子"，一句话，不超过30字。例如："瘦高个子，鞋上有泥，走路时右手一直攥着什么。"%s
                7. 最后用JSON格式返回章节内容，格式如下：
                {
                  "sceneText": "场景描写正文（自然地融入关键词和配角出场描写）...",
                  "options": [
                    {"id": 1, "text": "选项1文字", "hint": "隐藏提示：选择后可能的后果"},
                    {"id": 2, "text": "选项2文字", "hint": "隐藏提示：选择后可能的后果"},
                    {"id": 3, "text": "选项3文字", "hint": "隐藏提示：选择后可能的后果"}
                  ],
                  "characterAppearances": [
                    {"name": "张翁", "firstImpression": "一句话描述初见印象，不超过30字"}
                  ]
                  %s
                }
                """,
                styleName, chapterNo, "历史时代", styleName,
                getIdentityDesc(story.getIdentityType()),
                keywordsText, charactersText,
                story.getEntryAnswers(),
                getStyleGuidance(story.getStyle()),
                gestureGuidance,
                p04RainHint,
                keywordPositionSection
        );
    }

    // S-11: 根据手势轻重缓急返回对应的叙事口吻要求
    private String buildGestureGuidance(String gestureIntensity) {
        if (gestureIntensity == null || gestureIntensity.isEmpty()) {
            return "（无特殊手势，按默认节奏叙述）";
        }
        return switch (gestureIntensity) {
            case "gentle" -> "本章节请用轻柔舒缓的节奏叙述，如微风拂面，叙事悠缓从容，让情感在字里行间缓缓流淌。场景描写更偏意境，节奏放慢，给读者留出回味的空间。";
            case "urgent" -> "本章节请用紧凑急促的节奏叙述，叙事明快紧张，情节推进迅速，不拖泥带水。场景描写更偏动态，字句铿锵有力，让读者感受到时间紧迫与局势紧张。";
            case "forceful" -> "本章节请用浓烈有力的节奏叙述，戏剧张力强，情感浓烈鲜明，叙事有分量感。场景描写偏大气磅礴或细腻入微的对比，让读者感受到强烈的冲击与震撼。";
            default -> "（无特殊手势，按默认节奏叙述）";
        };
    }

    // P-04: 检测三水意象彩蛋
    // 水相关意象词：渡口/船/江/河/雨/水/舟/帆/潮/浪
    private static final List<String> WATER_KEYWORDS = List.of(
            "渡口", "船", "江", "河", "雨", "水", "舟", "帆", "潮", "浪"
    );

    private boolean isThreeWaterImagery(List<KeywordCard> keywords) {
        if (keywords == null || keywords.size() < 3) return false;
        // 至少需要3张关键词卡才能触发三水彩蛋
        if (keywords.size() < 3) return false;
        // 取前3张（通常是故事使用的3张关键词卡）
        List<KeywordCard> storyKeywords = keywords.size() > 3
                ? keywords.subList(0, 3)
                : keywords;
        // 检测：每张卡的 tag 或 name 中是否包含水相关意象词
        for (KeywordCard k : storyKeywords) {
            String tag = k.getTag();
            String name = k.getName() != null ? k.getName() : "";
            boolean hasWater = false;
            if (tag != null && !tag.isBlank()) {
                for (String w : WATER_KEYWORDS) {
                    if (tag.contains(w)) { hasWater = true; break; }
                }
            }
            if (!hasWater) {
                // fallback: 检查卡牌名称
                for (String w : WATER_KEYWORDS) {
                    if (name.contains(w)) { hasWater = true; break; }
                }
            }
            if (!hasWater) return false;
        }
        log.info("[P-04] 三水意象彩蛋触发！关键词: {}", storyKeywords.stream().map(KeywordCard::getName).toList());
        return true;
    }
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

    private String buildManuscriptSystemPromptWithTitles(Story story, List<KeywordCard> keywords,
                                                         List<StoryCharacter> characters) {
        return buildManuscriptSystemPromptWithTitles(story, keywords, characters, "");
    }

    private String buildManuscriptSystemPromptWithTitles(Story story, List<KeywordCard> keywords,
                                                         List<StoryCharacter> characters,
                                                         String emphasisPrompt) {
        String keywordList = keywords.stream().map(KeywordCard::getName).collect(Collectors.joining("、"));
        String emphasisSection = (emphasisPrompt != null && !emphasisPrompt.isBlank())
                ? "\n\n【关键词融入强调】" + emphasisPrompt
                : "";

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
                5. 生成3个备选标题（古典文学风格，各不超过10字）
                6. 输出完整的短篇小说正文（不含选项和提示）

                特别注意：
                - 禁止"宛如""仿佛""无法言说"等AI腔
                - 结局要有情感力量
                - 字数控制在3000-8000字
                - 标题要典雅、有意境%s

                请用以下JSON格式返回（必须严格遵守格式）：
                {
                  "manuscript": "完整的小说正文...",
                  "titles": ["标题1", "标题2", "标题3"]
                }
                """,
                keywordList.isEmpty() ? "无" : keywordList,
                getStyleName(story.getStyle()),
                getStyleName(story.getStyle()),
                emphasisSection
        );
    }

    private AIGatewayService.ManuscriptResult parseManuscriptWithTitlesResponse(String response) {
        try {
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}");
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
                String rawManuscriptText = (String) parsed.get("manuscript");
                String manuscriptText = aiPhraseFilter.filter(rawManuscriptText);
                @SuppressWarnings("unchecked")
                List<String> titles = (List<String>) parsed.get("titles");
                if (titles == null || titles.size() < 3) {
                    titles = List.of("时光旅人手记", "旧事新说", "一段往事");
                }
                // inscription 由 generateManuscriptWithTitles 单独生成
                return new AIGatewayService.ManuscriptResult(manuscriptText, titles, null);
            }
        } catch (Exception e) {
            log.warn("解析手稿（含标题）响应失败: {}, raw response={}", e.getMessage(), response);
        }
        // Fallback
        return new AIGatewayService.ManuscriptResult(
                response.length() > 100 ? aiPhraseFilter.filter(response) : "（手稿内容）",
                List.of("时光旅人手记", "旧事新说", "一段往事"),
                null
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
                String rawSceneText = (String) parsed.get("sceneText");
                chapter.setSceneText(aiPhraseFilter.filter(rawSceneText));

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

                // 解析配角初见印象
                List<Map<String, Object>> appearancesRaw = (List<Map<String, Object>>) parsed.get("characterAppearances");
                if (appearancesRaw != null) {
                    List<StoryChapter.CharacterAppearance> appearances = new ArrayList<>();
                    for (Map<String, Object> app : appearancesRaw) {
                        StoryChapter.CharacterAppearance appearance = new StoryChapter.CharacterAppearance();
                        appearance.setName((String) app.get("name"));
                        appearance.setFirstImpression((String) app.get("firstImpression"));
                        appearances.add(appearance);
                    }
                    chapter.setCharacterAppearances(appearances);
                }

                // E-07: 解析关键词落位
                List<Map<String, Object>> positionsRaw = (List<Map<String, Object>>) parsed.get("keywordPositions");
                if (positionsRaw != null && !positionsRaw.isEmpty()) {
                    List<StoryChapter.KeywordPosition> positions = new ArrayList<>();
                    for (Map<String, Object> pos : positionsRaw) {
                        StoryChapter.KeywordPosition kp = new StoryChapter.KeywordPosition();
                        kp.setKeyword((String) pos.get("keyword"));
                        kp.setRole((String) pos.get("role"));
                        kp.setRoleOwner((String) pos.get("roleOwner"));
                        positions.add(kp);
                    }
                    chapter.setKeywordPositions(positions);
                }
            } else {
                // 非JSON格式，整段作为场景描写
                chapter.setSceneText(aiPhraseFilter.filter(response));
            }
        } catch (Exception e) {
            log.error("解析章节响应失败: {}", e.getMessage());
            chapter.setSceneText(aiPhraseFilter.filter(response));
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

    /**
     * M-11 文学风格差异化指南
     * 4种风格应有明显差异：
     * - 白描（1）：写实主义，简洁白净，不加修饰
     * - 江湖（2）：豪放粗犷，刀光剑影，快意恩仇
     * - 笔记（3）：婉约细腻，人情世故，留白意境
     * - 话本（4）：史实严谨，考据翔实，客观叙述
     */
    private String getStyleGuidance(Integer style) {
        if (style == null) style = 1;
        return switch (style) {
            case 1 -> """
                【白描风格】写实主义，简洁白净
                - 语言：直白如话，不加藻饰，接近生活本真
                - 场景：用精确的细节代替形容，用动词推动叙事
                - 情感：不直接说"悲伤"，而是描写"他坐在门槛上很久"
                - 对话：简短有力，符合人物身份
                """;
            case 2 -> """
                【江湖风格】豪放粗犷，刀光剑影
                - 语言：大气磅礴，场面恢弘，笔触粗犷
                - 场景：多波澜壮阔的背景，武打场面或重要抉择
                - 情感：爱恨分明，悲喜浓烈，"拔刀相向"而非"默默离开"
                - 对话：掷地有声，常有豪言壮语
                """;
            case 3 -> """
                【笔记风格】婉约细腻，人情世故
                - 语言：古雅留白，有意境，有诗画感
                - 场景：重在人情往来，世态炎凉，一颦一笑皆文章
                - 情感：不直接表达，通过环境、动作、沉默传递
                - 对话：欲言又止，话外有音，"顾左右而言他"
                """;
            case 4 -> """
                【话本风格】史实严谨，考据翔实
                - 语言：客观叙述，冷静理性，少有主观色彩
                - 场景：还原历史现场，有时代特色，细节考据
                - 情感：旁观者视角，不偏不倚，让读者自己判断
                - 对话：符合历史语境，不使用现代词汇
                """;
            default -> "";
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
