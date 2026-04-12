package com.timespace.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * I-06 全局 XSS/SQL 注入防护过滤器
 *
 * 策略：
 * - 将所有请求包装为 XssHttpServletRequestWrapper
 * - 对参数、Header 进行 HTML 转义（JSoup 白名单模式）
 * - SQL 注入检测：危险模式命中返回 403
 * - /api/story/* 使用宽松富文本白名单
 * - Order 放在 CharacterEncodingFilter 之后（避免编码问题）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // CORS 预检请求放行
        if (httpRequest.getMethod().equalsIgnoreCase("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 包装请求，所有参数和 Header 都会经过 XSS/SQL 过滤
            chain.doFilter(new XssHttpServletRequestWrapper(httpRequest), response);
        } catch (SecurityException e) {
            // SQL 注入攻击：返回 403
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":403,\"message\":\"请求被拦截：检测到非法输入\"}");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // 无需初始化
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}
