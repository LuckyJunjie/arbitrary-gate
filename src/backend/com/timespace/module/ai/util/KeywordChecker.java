package com.timespace.module.ai.util;

import com.timespace.module.card.entity.KeywordCard;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关键词融入率检测工具
 *
 * 职责：
 * - 检查生成的手稿中关键词的出现次数
 * - 判断关键词是否有效融入（出现 ≥ 1 次）
 * - 生成未融入关键词的强调 prompt 片段
 *
 * 使用场景：
 * - 故事完结生成手稿后，调用 {@link #checkManuscript(String, List)} 检查关键词融入率
 * - 如果有未融入的词，返回强调 prompt，说书人重新生成时追加该 prompt
 * - 最多重试 2 次，防止死循环
 */
@Slf4j
public class KeywordChecker {

    /** 关键词最少出现次数（低于此值视为未融入） */
    private static final int MIN_OCCURRENCE = 1;

    /**
     * 检查手稿中关键词的融入情况
     *
     * @param manuscriptText 手稿正文
     * @param keywords      关键词卡列表
     * @return 检查结果，包含每个关键词的出现次数和未融入列表
     */
    public static CheckResult checkManuscript(String manuscriptText, List<KeywordCard> keywords) {
        if (manuscriptText == null || manuscriptText.isBlank()) {
            return CheckResult.builder()
                    .allIntegrated(false)
                    .keywordCounts(Collections.emptyMap())
                    .missingKeywords(keywords.stream()
                            .map(KeywordCard::getName)
                            .collect(Collectors.toList()))
                    .emphasisPrompt("")
                    .build();
        }

        Map<String, Integer> keywordCounts = new LinkedHashMap<>();
        List<String> missingKeywords = new ArrayList<>();

        for (KeywordCard keyword : keywords) {
            String name = keyword.getName();
            int count = countOccurrences(manuscriptText, name);
            keywordCounts.put(name, count);
            if (count < MIN_OCCURRENCE) {
                missingKeywords.add(name);
            }
        }

        boolean allIntegrated = missingKeywords.isEmpty();
        String emphasisPrompt = buildEmphasisPrompt(missingKeywords);

        log.info("[KeywordChecker] 检查完成: 总数={}, 融入={}, 缺失={}, 缺失词={}",
                keywords.size(), keywordCounts.size() - missingKeywords.size(),
                missingKeywords.size(), missingKeywords);

        return CheckResult.builder()
                .allIntegrated(allIntegrated)
                .keywordCounts(keywordCounts)
                .missingKeywords(missingKeywords)
                .emphasisPrompt(emphasisPrompt)
                .build();
    }

    /**
     * 检查手稿中关键词的融入情况（使用字符串列表）
     *
     * @param manuscriptText 手稿正文
     * @param keywordNames   关键词名称列表
     * @return 检查结果
     */
    public static CheckResult checkManuscript(String manuscriptText, List<String> keywordNames) {
        if (manuscriptText == null || manuscriptText.isBlank()) {
            return CheckResult.builder()
                    .allIntegrated(false)
                    .keywordCounts(Collections.emptyMap())
                    .missingKeywords(new ArrayList<>(keywordNames))
                    .emphasisPrompt("")
                    .build();
        }

        Map<String, Integer> keywordCounts = new LinkedHashMap<>();
        List<String> missingKeywords = new ArrayList<>();

        for (String name : keywordNames) {
            int count = countOccurrences(manuscriptText, name);
            keywordCounts.put(name, count);
            if (count < MIN_OCCURRENCE) {
                missingKeywords.add(name);
            }
        }

        boolean allIntegrated = missingKeywords.isEmpty();
        String emphasisPrompt = buildEmphasisPrompt(missingKeywords);

        return CheckResult.builder()
                .allIntegrated(allIntegrated)
                .keywordCounts(keywordCounts)
                .missingKeywords(missingKeywords)
                .emphasisPrompt(emphasisPrompt)
                .build();
    }

    /**
     * 统计关键词在文本中出现的次数
     * 使用精确匹配（区分大小写，但中文不区分大小写）
     */
    private static int countOccurrences(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return 0;
        }
        // 使用正则转义特殊字符，防止 ReDoS
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
     * 为未融入的关键词生成强调 prompt 片段
     * 追加到说书人的 system prompt 中，重新生成时强调这些词
     */
    private static String buildEmphasisPrompt(List<String> missingKeywords) {
        if (missingKeywords == null || missingKeywords.isEmpty()) {
            return "";
        }
        String keywordsStr = String.join("、", missingKeywords);
        return String.format(
                "\n\n【关键词强调】以下关键词尚未在手稿正文中融入，请务必在重新生成时将它们自然地编织到故事中：%s",
                keywordsStr
        );
    }

    /**
     * 检查结果
     */
    @lombok.Data
    @lombok.Builder
    public static class CheckResult {
        /** 所有关键词是否都已融入 */
        private boolean allIntegrated;

        /** 每个关键词的出现次数 */
        private Map<String, Integer> keywordCounts;

        /** 未融入的关键词列表 */
        private List<String> missingKeywords;

        /** 强调 prompt（追加到说书人 prompt 中） */
        private String emphasisPrompt;
    }
}
