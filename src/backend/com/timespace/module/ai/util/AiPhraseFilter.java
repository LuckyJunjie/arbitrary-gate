package com.timespace.module.ai.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 腔词黑名单过滤器
 *
 * AI-06 功能点：过滤 AI 生成文本中的腔调词语
 *
 * 黑名单词语来源：常见 AI 生成文本中的过度修辞、
 * 程式化表达、文绉绉的套话等。
 */
@Slf4j
public class AiPhraseFilter {

    /**
     * AI 腔词黑名单（去重后）
     */
    public static final Set<String> BLACKLIST;

    static {
        Set<String> set = new HashSet<>();
        // 比喻类腔词
        set.add("宛如");
        set.add("仿佛");
        set.add("仿若");
        set.add("似乎");
        set.add("宛若");
        set.add("恰如");
        set.add("恰似");
        set.add("犹如");
        set.add("犹");
        set.add("一如");
        set.add("一如既往");
        // 情感/心理描写腔词
        set.add("无法言说");
        set.add("难以言说");
        set.add("不可言说");
        set.add("难以名状");
        set.add("莫可名状");
        set.add("心有余悸");
        set.add("百感交集");
        set.add("不由自主");
        set.add("不约而同");
        set.add("不以为意");
        set.add("触手可及");
        set.add("举手投足");
        set.add("此时此刻");
        set.add("曾几何时");
        // 议论/过渡类腔词
        set.add("不难发现");
        set.add("众所周知");
        set.add("可以说");
        set.add("总的来说");
        set.add("值得注意的是");
        set.add("从某种意义上");
        set.add("想必");
        set.add("毋庸置疑");
        set.add("不言而喻");
        set.add("由此可见");
        set.add("总之");
        set.add("总而言之");
        set.add("事实上");
        set.add("实际上");
        set.add("说起来");
        set.add("话说回来");
        set.add("话虽如此");
        set.add("大概");
        set.add("或许");
        set.add("也许");
        set.add("恐怕");
        set.add("未免");
        set.add("多少有些");
        set.add("略显");
        set.add("略带");
        set.add("稍显");
        set.add("有些");
        set.add("多少有点");
        set.add("应该说");
        set.add("诚然");
        set.add("固");
        set.add("固然");
        set.add("当然");
        set.add("毫无疑问");
        set.add("无可否认");
        set.add("在这个意义上");
        set.add("从这个角度来说");
        set.add("首先");
        set.add("其次");
        set.add("再次");
        set.add("最后");
        set.add("第一");
        set.add("第二");
        set.add("第三");
        set.add("一方面");
        set.add("另一方面");
        set.add("综上所述");
        set.add("可以看出");
        set.add("毋庸讳言");
        set.add("无可置疑");
        set.add("有鉴于此");
        // 指代类腔词
        set.add("如此");
        set.add("这般");
        BLACKLIST = Collections.unmodifiableSet(set);
    }

    /**
     * 黑名单词的正则模式（预编译，按长度降序排列以优先匹配更长词）
     */
    private static final List<Pattern> PATTERNS;

    static {
        // 按长度降序排列，确保先匹配长词（如"一如既往"优先于"一如"）
        List<String> sorted = BLACKLIST.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());

        PATTERNS = sorted.stream()
                .map(word -> Pattern.compile(
                        "\\Q" + word + "\\E",
                        Pattern.UNICODE_CASE
                ))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> Collections.unmodifiableList(list)
                ));
    }

    /**
     * 过滤文本中的 AI 腔词，替换为近义自然表达
     *
     * @param text 原始文本
     * @return 过滤后的文本
     */
    public static String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;
        for (Pattern pattern : PATTERNS) {
            result = pattern.matcher(result).replaceAll(getReplacement(result, pattern.pattern()));
        }
        return result;
    }

    /**
     * 检测文本中是否包含黑名单词
     *
     * @param text 待检测文本
     * @return 是否包含
     */
    public static boolean containsBlockedPhrase(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出文本中所有黑名单词
     *
     * @param text 待检测文本
     * @return 黑名单词列表（去重）
     */
    public static List<String> getBlockedPhrases(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> found = new LinkedHashSet<>();
        for (String word : BLACKLIST) {
            Pattern p = Pattern.compile("\\Q" + word + "\\E", Pattern.UNICODE_CASE);
            if (p.matcher(text).find()) {
                found.add(word);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * 根据被替换的词返回合适的替代字符串
     * 优先保留原文语义，移除腔调词
     */
    private static String getReplacement(String text, String matchedWord) {
        // 对于功能性连接词，直接删除（保留句子流畅）
        // 对于比喻词，用更自然的表达替换

        // 直接删除的词（功能性过渡/连接词）
        Set<String> deleteWords = Set.of(
                "似乎", "仿佛", "仿若", "宛如", "宛若", "恰如", "恰似",
                "犹如", "犹", "一如", "一如既往",
                "无法言说", "难以言说", "不可言说", "难以名状", "莫可名状",
                "心有余悸", "百感交集", "不由自主", "不约而同", "不以为意",
                "触手可及", "举手投足", "此时此刻", "曾几何时",
                "不难发现", "众所周知", "可以说", "总的来说",
                "值得注意的是", "从某种意义上", "想必", "毋庸置疑",
                "不言而喻", "由此可见", "总之", "总而言之",
                "事实上", "实际上", "说起来", "话说回来", "话虽如此",
                "大概", "或许", "也许", "恐怕", "未免", "多少有些",
                "略显", "略带", "稍显", "有些", "多少有点", "应该说",
                "诚然", "固", "固然", "当然", "毫无疑问", "无可否认",
                "在这个意义上", "从这个角度来说",
                "首先", "其次", "再次", "最后",
                "第一", "第二", "第三", "一方面", "另一方面",
                "综上所述", "可以看出", "毋庸讳言", "无可置疑", "有鉴于此",
                "如此", "这般"
        );

        String word = matchedWord;
        if (deleteWords.contains(word)) {
            return "";
        }

        return "";
    }
}
