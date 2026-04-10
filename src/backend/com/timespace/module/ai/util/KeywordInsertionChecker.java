package com.timespace.module.ai.util;

import com.timespace.module.card.entity.KeywordCard;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI-09: 关键词融入率检测
 *
 * 职责：
 * - 检测手稿正文中是否包含 ≥3 个用户选择的关键词
 * - 如果少于 3 个，生成警告注释（不阻止展示）
 * - 与 {@link KeywordChecker} 的区别：
 *   - KeywordChecker：AI-09 内部使用，检测关键词是否全部融入（出现 ≥1 次），用于 AI 重试
 *   - KeywordInsertionChecker：对外接口，检测是否达到 ≥3 个融合阈值，用于给用户提示
 *
 * 使用场景：
 * - 在 {@link com.timespace.module.story.service.StoryOrchestrationService#finishStory} 中调用
 * - 生成手稿后检测，如果 < 3 个，添加警告注释
 */
@Slf4j
public class KeywordInsertionChecker {

    /** 关键词最少出现次数（低于此值视为未融入） */
    private static final int MIN_OCCURRENCE = 1;

    /** 关键词融合阈值：至少需要 ≥3 个关键词 */
    private static final int INTEGRATION_THRESHOLD = 3;

    /**
     * 检测手稿中关键词的融入情况
     *
     * @param manuscriptText 手稿正文
     * @param keywords     关键词卡列表
     * @return 检测结果，包含警告信息和是否达标
     */
    public static CheckResult check(String manuscriptText, List<KeywordCard> keywords) {
        if (manuscriptText == null || manuscriptText.isBlank()) {
            return CheckResult.builder()
                    .integratedCount(0)
                    .totalCount(keywords.size())
                    .integratedKeywordNames(Collections.emptyList())
                    .warning(buildWarning(0, keywords.size()))
                    .build();
        }

        List<String> integratedKeywords = new ArrayList<>();

        for (KeywordCard keyword : keywords) {
            String name = keyword.getName();
            int count = countOccurrences(manuscriptText, name);
            if (count >= MIN_OCCURRENCE) {
                integratedKeywords.add(name);
            }
        }

        int integratedCount = integratedKeywords.size();
        boolean sufficient = integratedCount >= INTEGRATION_THRESHOLD;
        String warning = sufficient ? null : buildWarning(integratedCount, keywords.size());

        log.info("[KeywordInsertionChecker] 检查完成: 融入数={}/{}, 阈值={}, 达标={}, 融入词={}",
                integratedCount, keywords.size(), INTEGRATION_THRESHOLD, sufficient, integratedKeywords);

        return CheckResult.builder()
                .integratedCount(integratedCount)
                .totalCount(keywords.size())
                .integratedKeywordNames(integratedKeywords)
                .warning(warning)
                .build();
    }

    /**
     * 检测手稿中关键词的融入情况（使用字符串列表）
     *
     * @param manuscriptText 手稿正文
     * @param keywordNames   关键词名称列表
     * @return 检测结果
     */
    public static CheckResult check(String manuscriptText, List<String> keywordNames) {
        if (manuscriptText == null || manuscriptText.isBlank()) {
            return CheckResult.builder()
                    .integratedCount(0)
                    .totalCount(keywordNames.size())
                    .integratedKeywordNames(Collections.emptyList())
                    .warning(buildWarning(0, keywordNames.size()))
                    .build();
        }

        List<String> integratedKeywords = new ArrayList<>();

        for (String name : keywordNames) {
            int count = countOccurrences(manuscriptText, name);
            if (count >= MIN_OCCURRENCE) {
                integratedKeywords.add(name);
            }
        }

        int integratedCount = integratedKeywords.size();
        boolean sufficient = integratedCount >= INTEGRATION_THRESHOLD;
        String warning = sufficient ? null : buildWarning(integratedCount, keywordNames.size());

        return CheckResult.builder()
                .integratedCount(integratedCount)
                .totalCount(keywordNames.size())
                .integratedKeywordNames(integratedKeywords)
                .warning(warning)
                .build();
    }

    /**
     * 统计关键词在文本中出现的次数
     * 使用精确匹配，中文友好
     */
    private static int countOccurrences(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return 0;
        }
        String escapedKeyword = Pattern.quote(keyword);
        Pattern pattern = Pattern.compile(escapedKeyword);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 生成警告信息
     */
    private static String buildWarning(int integratedCount, int totalCount) {
        return String.format(
                "【关键词融入提醒】本篇手稿仅融入 %d/%d 个关键词，建议重新生成以获得更完整的故事体验。",
                integratedCount, totalCount
        );
    }

    /**
     * 检测结果
     */
    @lombok.Data
    @lombok.Builder
    public static class CheckResult {
        /** 实际融入的关键词数量 */
        private int integratedCount;

        /** 关键词总数 */
        private int totalCount;

        /** 已融入的关键词名称列表 */
        private List<String> integratedKeywordNames;

        /** 警告信息（仅在未达标时非空） */
        private String warning;

        /**
         * 是否达标（≥3 个关键词融入）
         */
        public boolean isSufficient() {
            return integratedCount >= INTEGRATION_THRESHOLD;
        }
    }
}
