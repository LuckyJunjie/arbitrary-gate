package com.timespace.module.ai.agent;

import com.timespace.module.ai.client.AIClient;
import com.timespace.module.ai.service.AiPromptTemplateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 掌眼 Agent
 *
 * 职责：剔除 AI 腔词汇，提高文学质感
 *
 * 处理策略：
 * 1. 黑名单正则替换（主路径，快速且零成本）
 * 2. 可选 AI 二次润色（精确但有成本）
 *
 * 黑名单词汇表：
 * 宛如、仿佛、无法言说、不禁、缓缓说道、轻声说道、
 * 目光中满是、心中一动、似乎在诉说
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhangyanAgent {

    private final AIClient aiClient;
    private final AiPromptTemplateService promptTemplateService;

    // AI-07: 默认润色系统 Prompt（fallback 用）
    private static final String DEFAULT_POLISH_SYSTEM_PROMPT = """
            你是一位严苛的老编辑，精通中国古典文学。

            你的任务是检查并润色文本，使其更接近人类写作的自然质感。

            润色原则：
            1. 去除所有AI腔表达（"宛如"、"仿佛"、"缓缓"、"轻轻"等）
            2. 保留原文的故事线和情感
            3. 用词要像真实的古典小说，不刻意对仗工整
            4. 不要过度润色，只改明显AI腔的地方
            5. 直接返回润色后的完整正文，不要加任何说明
            """;

    // AI-07: 运行时 Prompt 模板（从 DB 加载）
    private String polishSystemPromptTemplate;

    /**
     * 黑名单词表（静态编译，提升性能）
     */
    private static final List<String> BLACKLIST = List.of(
            "宛如",
            "仿佛",
            "无法言说",
            "不禁",
            "缓缓说道",
            "轻声说道",
            "目光中满是",
            "心中一动",
            "似乎在诉说"
    );

    /**
     * 替换表：将 AI 腔词汇映射为更自然的表达
     * 部分词汇需要根据上下文判断，以下为通用替换
     */
    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement("宛如", "像"),
            new Replacement("仿佛", "好像"),
            new Replacement("无法言说", "说不清"),
            new Replacement("不禁", ""),           // 直接删除
            new Replacement("缓缓说道", "说"),
            new Replacement("轻声说道", "低声道"),
            new Replacement("目光中满是", "眼里尽是"),
            new Replacement("心中一动", "心头一紧"),
            new Replacement("似乎在诉说", "像是在说")
    );

    // AI-07: 启动时从数据库加载 Prompt 模板，失败时 fallback 到硬编码默认值
    @PostConstruct
    public void loadPromptsFromDatabase() {
        log.info("[AI-07] 掌眼 Agent 正在加载 Prompt 模板...");

        this.polishSystemPromptTemplate = promptTemplateService.getPromptTextOrDefault(
                AiPromptTemplateService.AGENT_ZHANGYAN,
                AiPromptTemplateService.PROMPT_AI腔_FILTER,
                DEFAULT_POLISH_SYSTEM_PROMPT
        );
        log.info("[AI-07] 润色系统 Prompt 加载完成, length={}", polishSystemPromptTemplate.length());
    }

    /**
     * 过滤主方法 — 纯正则处理，快速过滤 AI 腔
     *
     * @param text 原始文本
     * @return 过滤后文本
     */
    public String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;

        for (Replacement rep : REPLACEMENTS) {
            Pattern pattern = Pattern.compile(
                    "(?<=[\\u4e00-\\u9fff])"   // 前置汉字边界，防止误匹配英文
                            + Pattern.quote(rep.target())
                            + "(?=[\\u4e00-\\u9fff\\s。，、；：！？])"  // 后置汉字或标点
            );
            Matcher matcher = pattern.matcher(result);
            if (rep.replacement().isEmpty()) {
                // 空字符串替换 = 删除
                result = matcher.replaceAll("");
            } else {
                result = matcher.replaceAll(rep.replacement());
            }
        }

        // 清理连续空格（删除词汇后可能产生）
        result = result.replaceAll("\\s{2,}", " ");
        // 清理可能的句首残留标点
        result = result.replaceAll("^[，。、；：！？,\\s]+", "");

        log.debug("掌眼过滤完成，原文长度={}, 过滤后长度={}", text.length(), result.length());
        return result;
    }

    /**
     * AI 二次润色（可选，精确但有成本）
     * 在正则过滤后调用，对文本进行更自然的改写
     *
     * @param text 正则过滤后的文本
     * @return 润色后文本
     */
    public String polish(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        log.info("掌眼 AI 二次润色启动，文本长度={}", text.length());

        String userMessage = "请润色以下文本：\n\n" + text;

        try {
            String polished = aiClient.callSync(polishSystemPromptTemplate, userMessage);
            log.info("掌眼 AI 润色完成，输出长度={}", polished.length());
            return polished;
        } catch (Exception e) {
            log.error("掌眼 AI 润色失败，回退到正则过滤结果: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 完整处理流程：正则过滤 → 可选 AI 润色
     *
     * @param text       原始文本
     * @param enablePolish 是否启用 AI 二次润色
     * @return 最终文本
     */
    public String process(String text, boolean enablePolish) {
        String filtered = filter(text);
        if (enablePolish) {
            return polish(filtered);
        }
        return filtered;
    }

    /**
     * 返回黑名单词表（供测试/调试用）
     */
    public List<String> getBlacklist() {
        return BLACKLIST;
    }

    /**
     * 检查文本中是否包含黑名单词汇（供测试用）
     *
     * @param text 待检查文本
     * @return 被捕获的黑名单词列表
     */
    public List<String> findBlacklistWords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return BLACKLIST.stream()
                .filter(word -> text.contains(word))
                .toList();
    }

    /**
     * 替换规则
     *
     * @param target      黑名单词
     * @param replacement 替换目标（空=删除）
     */
    private record Replacement(String target, String replacement) {}
}
