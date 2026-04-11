package com.timespace.module.wechat.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SH-05 微信 JSSDK 服务
 * <p>
 * 负责获取并缓存 access_token 和 jsapi_ticket，
 * 以及生成 JSSDK 签名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatService {

    private static final String ACCESS_TOKEN_KEY = "wechat:access_token";
    private static final String JSAPI_TICKET_KEY = "wechat:jsapi_ticket";
    private static final int ACCESS_TOKEN_TTL_SECONDS = 7000; // 提前 100s 刷新
    private static final int JSAPI_TICKET_TTL_SECONDS = 7000;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplateWrapper restTemplate;

    @Value("${timespace.wechat.app-id:#{null}}")
    private String appId;

    @Value("${timespace.wechat.app-secret:#{null}}")
    private String appSecret;

    /**
     * 获取 access_token（优先从 Redis 缓存获取）
     */
    public String getAccessToken() {
        // 1. 尝试从 Redis 获取
        String cached = redisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isEmpty()) {
            log.debug("WeChat access_token 从缓存获取");
            return cached;
        }

        // 2. 从微信服务器获取
        if (appId == null || appSecret == null || appId.isEmpty()) {
            throw new IllegalStateException("微信 appId 或 appSecret 未配置");
        }

        String url = String.format(
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                appId, appSecret);

        try {
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(resp);
            if (node.has("access_token")) {
                String token = node.get("access_token").asText();
                int expiresIn = node.get("expires_in").asInt();
                redisTemplate.opsForValue().set(
                        ACCESS_TOKEN_KEY, token,
                        Math.min(expiresIn - 100, ACCESS_TOKEN_TTL_SECONDS), TimeUnit.SECONDS);
                log.info("WeChat access_token 已刷新，有效期 {}s", expiresIn);
                return token;
            } else {
                log.error("获取微信 access_token 失败: {}", resp);
                throw new RuntimeException("获取微信 access_token 失败: " + node.path("errmsg").asText());
            }
        } catch (Exception e) {
            log.error("获取微信 access_token 异常", e);
            throw new RuntimeException("获取微信 access_token 异常", e);
        }
    }

    /**
     * 获取 jsapi_ticket（优先从 Redis 缓存获取）
     */
    public String getJsapiTicket() {
        // 1. 尝试从 Redis 获取
        String cached = redisTemplate.opsForValue().get(JSAPI_TICKET_KEY);
        if (cached != null && !cached.isEmpty()) {
            log.debug("WeChat jsapi_ticket 从缓存获取");
            return cached;
        }

        // 2. 从微信服务器获取
        String token = getAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=" + token + "&type=jsapi";

        try {
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(resp);
            if ("0".equals(node.path("errcode").asText())) {
                String ticket = node.get("ticket").asText();
                int expiresIn = node.get("expires_in").asInt();
                redisTemplate.opsForValue().set(
                        JSAPI_TICKET_KEY, ticket,
                        Math.min(expiresIn - 100, JSAPI_TICKET_TTL_SECONDS), TimeUnit.SECONDS);
                log.info("WeChat jsapi_ticket 已刷新，有效期 {}s", expiresIn);
                return ticket;
            } else {
                log.error("获取微信 jsapi_ticket 失败: {}", resp);
                throw new RuntimeException("获取微信 jsapi_ticket 失败: " + node.path("errmsg").asText());
            }
        } catch (Exception e) {
            log.error("获取微信 jsapi_ticket 异常", e);
            throw new RuntimeException("获取微信 jsapi_ticket 异常", e);
        }
    }

    /**
     * 生成 JSSDK 签名
     *
     * @param url 当前页面 URL（必须是 JSSDK 初始化时调用 config 的页面 URL）
     * @return 签名所需的参数（appId, timestamp, nonceStr, signature）
     */
    public Map<String, String> buildJsapiConfig(String url) {
        String jsapiTicket = getJsapiTicket();
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = UUID.randomUUID().toString().replace("-", "");

        // 构造签名串：jsapi_ticket、timestamp、nonceStr、url
        String signString = "jsapi_ticket=" + jsapiTicket +
                "&noncestr=" + nonceStr +
                "&timestamp=" + timestamp +
                "&url=" + url;

        String signature = DigestUtil.sha1Hex(signString);

        Map<String, String> config = new HashMap<>();
        config.put("appId", appId);
        config.put("timestamp", String.valueOf(timestamp));
        config.put("nonceStr", nonceStr);
        config.put("signature", signature);

        log.debug("JSSDK 签名生成完毕，url={}", url);
        return config;
    }

    /**
     * 简易 RestTemplate 封装（避免额外依赖）
     * 实际项目建议使用 WebClient 或 RestTemplate Bean
     */
    @lombok.Component
    @RequiredArgsConstructor
    public static class RestTemplateWrapper {
        private final org.springframework.web.client.RestTemplate restTemplate;

        public <T> T getForObject(String url, Class<T> type) {
            return restTemplate.getForObject(url, type);
        }
    }
}
