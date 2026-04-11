package com.timespace.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * I-06 全局 XSS 注入防护过滤器
 * 将所有请求包装为 XssHttpServletRequestWrapper，
 * 对参数、Header 进行 HTML 转义，防止 XSS 注入攻击
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 仅对 HTTP 请求启用
        if (httpRequest.getMethod().equalsIgnoreCase("OPTIONS")) {
            // CORS 预检请求放行
            chain.doFilter(request, response);
            return;
        }
        // 包装请求，所有参数和 Header 都会经过转义
        chain.doFilter(new XssHttpServletRequestWrapper(httpRequest), response);
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
