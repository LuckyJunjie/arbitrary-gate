package com.timespace.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * I-06 XSS 注入防护请求包装器
 * 对所有请求参数、Header 进行 HTML 转义，防止 XSS 注入
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
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
     * HTML 特殊字符转义，防止 XSS
     * 转义规则：
     *   < → &lt;
     *   > → &gt;
     *   " → &quot;
     *   ' → &#x27;  (不完全引号，避免被某些框架解释)
     *   & → &amp;   (最后转义，避免&本身被二次转义)
     */
    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        // 先转义 &，避免已转义内容被二次处理
        String escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
        return escaped;
    }
}
