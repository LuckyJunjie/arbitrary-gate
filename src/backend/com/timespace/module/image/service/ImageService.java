package com.timespace.module.image.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * AI 图片生成服务
 * C-14 AI画师对接
 *
 * 调用通义万相（wanx2.1）API 生成图片
 * - 关键词卡配图（512*768 竖版）
 * - 场景图（1024*1024 方版）
 * - 缓存：相同 prompt hash 24h 内不重复生成
 */
@Slf4j
@Service
public class ImageService {

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String WANX_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";

    private static final String CACHE_PREFIX = "img:cache:";
    private static final long CACHE_TTL_HOURS = 24;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    public ImageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成图片
     *
     * @param prompt 英文 prompt（由前端 aiPainter.ts 构建）
     * @param size   图片尺寸，默认 512*768
     * @return 生成结果含图片 URL
     */
    public ImageGenerateResult generateImage(String prompt, String size) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        String cacheKey = hashPrompt(prompt);
        String cached = getCachedUrl(cacheKey);
        if (cached != null) {
            log.info("[C-14] 图片缓存命中: hash={}", cacheKey);
            return new ImageGenerateResult(cached, true);
        }

        String imageUrl = callWanxApi(prompt, size != null ? size : "512*768");
        setCachedUrl(cacheKey, imageUrl);
        return new ImageGenerateResult(imageUrl, false);
    }

    /**
     * 调用通义万相 wanx2.1 API
     */
    private String callWanxApi(String prompt, String size) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[C-14] DASHSCOPE_API_KEY 未配置，使用占位图");
            return buildPlaceholderUrl(prompt);
        }

        try {
            // 构建请求体
            String requestBody = String.format("""
                {
                  "model": "wanx2.1",
                  "input": { "prompt": %s },
                  "parameters": {
                    "size": "%s",
                    "steps": 20,
                    "n": 1
                  }
                }
                """, toJsonString(prompt), size);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            var entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
            var response = restTemplate.postForEntity(WANX_URL, entity, String.class);

            String body = response.getBody();
            if (body == null) {
                throw new RuntimeException("wanx API 响应为空");
            }

            // 解析 JSON 提取 image URL
            // 响应格式：{"data":{"result_url":"https://..."}}
            String imageUrl = extractResultUrl(body);
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new RuntimeException("wanx 响应中无 result_url: " + body);
            }

            log.info("[C-14] 图片生成成功: size={}, urlLen={}", size, imageUrl.length());
            return imageUrl;

        } catch (Exception e) {
            log.error("[C-14] 通义万相 API 调用失败: {}", e.getMessage(), e);
            return buildPlaceholderUrl(prompt);
        }
    }

    /**
     * 从 wanx 响应 JSON 中提取 result_url
     */
    private String extractResultUrl(String jsonBody) {
        try {
            // 简单解析，避免引入额外依赖
            int idx = jsonBody.indexOf("\"result_url\"");
            if (idx == -1) return null;
            int colon = jsonBody.indexOf(":", idx);
            int start = jsonBody.indexOf("\"", colon + 1);
            int end = jsonBody.indexOf("\"", start + 1);
            if (start == -1 || end == -1) return null;
            return jsonBody.substring(start + 1, end);
        } catch (Exception e) {
            log.warn("[C-14] 解析 result_url 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成占位图 URL（SVG data URI）
     */
    private String buildPlaceholderUrl(String prompt) {
        String text = prompt.length() > 20 ? prompt.substring(0, 20) : prompt;
        return "data:image/svg+xml," + java.net.URLEncoder.encode(
                "<svg xmlns='http://www.w3.org/2000/svg' width='280' height='400'>" +
                "<rect width='280' height='400' fill='#E8E0D5'/>" +
                "<rect x='20' y='20' width='240' height='360' fill='none' stroke='%238B5E3C' stroke-width='2' rx='4'/>" +
                "<text x='140' y='200' font-family='serif' font-size='24' fill='%238B5E3C' text-anchor='middle'>[Image]</text>" +
                "</svg>",
                StandardCharsets.UTF_8);
    }

    /**
     * Prompt SHA-256 哈希（作为缓存 key）
     */
    private String hashPrompt(String prompt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return prompt.substring(0, Math.min(prompt.length(), 32));
        }
    }

    private String getCachedUrl(String hash) {
        try {
            return redisTemplate.opsForValue().get(CACHE_PREFIX + hash);
        } catch (Exception e) {
            return null;
        }
    }

    private void setCachedUrl(String hash, String url) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + hash, url, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[C-14] 缓存写入失败: {}", e.getMessage());
        }
    }

    private String toJsonString(String s) {
        // 简单转义双引号和换行
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Data
    public static class ImageGenerateResult {
        private final String imageUrl;
        private final boolean cached;

        public ImageGenerateResult(String imageUrl, boolean cached) {
            this.imageUrl = imageUrl;
            this.cached = cached;
        }
    }
}
