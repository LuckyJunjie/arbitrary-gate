package com.timespace.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // I-07: Rate Limiting 拦截器（限流 /api/share/{code} GET 请求，防止枚举攻击）
        // 放在 Sa-Token 之前，order 数值越小越先执行
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/share/*")
                .excludePathPatterns(
                        "/api/share/create",
                        "/api/share/special-cards",
                        "/api/share/commemorative-cards",
                        "/api/share/commemorative-card/*",
                        "/api/share/*/joint"
                )
                .order(1);

        // Sa-Token 认证拦截器（排除登录等公开接口）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/wx-login",
                        "/api/user/register",
                        "/api/health",
                        "/ws/**"
                );
    }
}
