package com.timespace.common.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket STOMP 配置
 *
 * 前端连接方式：
 * 1. 连接 /ws (SockJS 或原生 WebSocket)
 * 2. STOMP 订阅 /user/queue/chapter_stream  → 章节流式片段
 * 3. STOMP 订阅 /user/queue/choice_result    → 判官评估结果
 * 4. STOMP 订阅 /user/queue/story_end         → 故事完成
 * 5. STOMP 订阅 /user/queue/judgment          → 判官判词
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置户内消息代理（推送目标前缀）
        registry.enableSimpleBroker("/queue", "/topic");
        // 应用目标前缀（客户端发送消息时使用）
        registry.setApplicationDestinationPrefixes("/app");
        // 用户专属目标前缀（用于 @SendToUser）
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点（支持 SockJS 回退）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 原生 WebSocket 端点（无需 SockJS）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 从 header 中提取 token 并认证
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");
                    if (authHeaders != null && !authHeaders.isEmpty()) {
                        String token = authHeaders.get(0);
                        if (token.startsWith("Bearer ")) {
                            token = token.substring(7);
                        }
                        try {
                            // Sa-Token 验证 token 并获取登录 ID
                            Object loginId = StpUtil.getLoginIdByToken(token);
                            accessor.setUser(new java.security.Principal() {
                                @Override
                                public String getName() {
                                    return String.valueOf(loginId);
                                }
                            });
                        } catch (Exception e) {
                            // 认证失败，后续会话操作会被拦截
                        }
                    }
                }
                return message;
            }
        });
    }
}
