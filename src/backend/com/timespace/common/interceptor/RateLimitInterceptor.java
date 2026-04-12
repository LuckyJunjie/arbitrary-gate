package com.timespace.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * I-07 分享码不可枚举 — IP级别 Rate Limiting 拦截器
 *
 * 策略：
 * - 基于 IP + 请求路径的滑动窗口限流
 * - 限制：同一IP 60秒内最多请求 /api/share/{code} 10次
 * - 使用 Redis INCR + EXPIRE 实现
 * - 超限返回 HTTP 429 Too Many Requests
 *
 * 注意：只拦截 GET 请求（读操作），POST joint 操作不限（需要登录）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 限流窗口时间（秒） */
    private static final int WINDOW_SECONDS = 60;

    /** 限流阈值（同一IP在窗口内最多请求次数） */
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    /** Redis Key 前缀 */
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:share:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 只拦截 GET 请求
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();

        // 只拦截 /api/share/* 但排除 /api/share/special-cards 和 /api/share/commemorative-card/*
        if (!uri.startsWith("/api/share/") || uri.equals("/api/share/special-cards")
                || uri.equals("/api/share/commemorative-cards") || uri.equals("/api/share/create")
                || uri.startsWith("/api/share/commemorative-card/")) {
            return true;
        }

        String clientIp = getClientIp(request);
        String redisKey = RATE_LIMIT_KEY_PREFIX + clientIp;

        try {
            Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
                // Redis 异常，放行（不要因为限流组件导致服务不可用）
                log.warn("RateLimit Redis INCR 失败，放行请求: ip={}", clientIp);
                return true;
            }

            if (currentCount == 1) {
                // 第一次请求，设置过期时间
                stringRedisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            if (currentCount > MAX_REQUESTS_PER_WINDOW) {
                log.warn("RateLimit 触发: ip={}, count={}, uri={}", clientIp, currentCount, uri);
                writeTooManyRequestsResponse(response);
                return false;
            }

        } catch (Exception e) {
            // Redis 异常时放行，不要阻塞正常请求
            log.warn("RateLimit Redis 操作异常，放行请求: ip={}, error={}", clientIp, e.getMessage());
        }

        return true;
    }

    /**
     * 获取真实客户端 IP（支持代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个IP时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 写入 429 Too Many Requests 响应
     */
    private void writeTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
    }
}
