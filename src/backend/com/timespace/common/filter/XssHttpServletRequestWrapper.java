package com.timespace.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * I-06 XSS + SQL 注入防护请求包装器
 *
 * 安全策略：
 * 1. XSS 防护：使用 JSoup.clean() 白名单模式转义 HTML 特殊字符
 * 2. SQL 注入检测：禁止参数中出现危险 SQL 模式，命中则返回 403
 * 3. /api/story/* 故事内容接口：使用更宽松的富文本白名单（允许部分 HTML 标签）
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final boolean isStoryContentPath;

    // SQL 注入危险模式（不区分大小写）
    private static final List<Pattern> SQL_DANGER_PATTERNS = List.of(
            Pattern.compile("(?i).*'\\s*(OR|AND)\\s*'.*"),
            Pattern.compile("(?i).*;\\s*DROP\\s+.*"),
            Pattern.compile("(?i).*;\\s*DELETE\\s+.*"),
            Pattern.compile("(?i).*;\\s*TRUNCATE\\s+.*"),
            Pattern.compile("(?i).*;\\s*INSERT\\s+.*"),
            Pattern.compile("(?i).*;\\s*UPDATE\\s+.*"),
            Pattern.compile("(?i).*UNION\\s+SELECT\\s+.*"),
            Pattern.compile("(?i).*UNION\\s+ALL\\s+SELECT\\s+.*"),
            Pattern.compile("(?i).*EXEC(\\s|\\(|;).*"),
            Pattern.compile("(?i).*XP_.*"),
            Pattern.compile("(?i).*LOAD_FILE\\s*\\(.*"),
            Pattern.compile("(?i).*INTO\\s+OUTFILE\\s+.*"),
            Pattern.compile("(?i).*SLEEP\\s*\\(.*"),
            Pattern.compile("(?i).*BENCHMARK\\s*\\(.*"),
            Pattern.compile("(?i).*WAITFOR\\s+DELAY\\s+.*")
    );

    // 普通接口白名单（仅转义，无 HTML 标签）
    private static final Safelist PLAIN_TEXT_SAFELIST = Safelist.none();

    // 故事内容白名单（允许部分格式标签，用于 rich text 场景）
    private static final Safelist STORY_CONTENT_SAFELIST = Safelist.relaxed()
            .removeTags("a", "img", "audio", "video", "iframe")
            .removeAttributes("a", "href")
            .removeAttributes("img", "src", "alt")
            .removeProtocols("img", "src", "http", "https", "data");

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        String uri = request.getRequestURI();
        this.isStoryContentPath = uri != null && uri.startsWith("/api/story/");
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitize(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        return Arrays.stream(values)
                .map(this::sanitize)
                .toArray(String[]::new);
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return sanitize(value);
    }

    /**
     * 核心安全方法：
     * 1. SQL 注入检测（所有接口）
     * 2. JSoup.clean() HTML 转义
     */
    private String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        // 1. SQL 注入检测（所有接口通用）
        if (containsSqlInjection(trimmed)) {
            throw new SecurityException("SQL injection detected");
        }

        // 2. JSoup HTML 净化
        // story 内容接口允许宽松白名单，普通接口使用严格白名单
        Safelist safelist = isStoryContentPath ? STORY_CONTENT_SAFELIST : PLAIN_TEXT_SAFELIST;
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .escapeMode(org.jsoup.nodes.entities.EscapeMode.xhtml)
                .prettyPrint(false);

        String cleaned = Jsoup.clean(trimmed, "", safelist, outputSettings);
        return cleaned;
    }

    /**
     * SQL 注入检测
     * @return true if dangerous SQL pattern found
     */
    private boolean containsSqlInjection(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (Pattern pattern : SQL_DANGER_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }
}
