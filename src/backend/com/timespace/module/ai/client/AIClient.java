package com.timespace.module.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * AI 客户端 - 直连通义千问 API（阿里云百炼）
 *
 * 通义千问 OpenAI 兼容接口：
 *   POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 */
@Slf4j
@Component
public class AIClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-turbo}")
    private String defaultModel;

    private static final String CHAT_COMPLETIONS_URL =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    public AIClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ── 同步调用 ──────────────────────────────────────────────────────────────

    /**
     * 同步调用（阻塞），用于判官评估、选项生成等短文本任务
     */
    public String callSync(String systemPrompt, String userMessage) {
        log.debug("[AI] 同步调用开始: model={}, systemLen={}, userLen={}",
                defaultModel, systemPrompt.length(), userMessage.length());

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", defaultModel);

        ArrayNode messages = requestBody.putArray("messages");
        messages.add(makeMessage("system", systemPrompt));
        messages.add(makeMessage("user", userMessage));

        try {
            HttpHeaders headers = authHeaders();
            HttpEntity<ObjectNode> entity = new HttpEntity<>(requestBody, headers);

            String url = baseUrl + "/compatible-mode/v1/chat/completions";
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            String responseText = parseChoiceText(resp.getBody());
            log.debug("[AI] 同步响应: len={}", responseText != null ? responseText.length() : 0);
            return responseText;

        } catch (RestClientException e) {
            log.error("[AI] 同步调用失败: {}", e.getMessage(), e);
            throw new com.timespace.common.exception.BusinessException(
                    500, "AI服务调用失败: " + e.getMessage());
        }
    }

    // ── 流式调用（WebSocket 实时推送） ─────────────────────────────────────

    /**
     * 流式调用（逐块回调），用于说书人生成故事章节
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param onChunk      每个文本片段的回调（用于 WebSocket/SSE 推送）
     * @return 完整响应文本
     */
    public String callStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        log.info("[AI] 流式调用开始: model={}", defaultModel);
        StringBuilder fullResponse = new StringBuilder();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", defaultModel);
        requestBody.put("stream", true);

        ArrayNode messages = requestBody.putArray("messages");
        messages.add(makeMessage("system", systemPrompt));
        messages.add(makeMessage("user", userMessage));

        try {
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 接收 SSE stream
            HttpEntity<ObjectNode> entity = new HttpEntity<>(requestBody, headers);

            String url = baseUrl + "/compatible-mode/v1/chat/completions";
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, byte[].class);

            // 流式响应体在 resp.getBody() 中，为 SSE 格式文本
            if (resp.getBody() != null) {
                String raw = new String(resp.getBody(), StandardCharsets.UTF_8);
                for (String line : raw.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("data:") && !line.equals("data: [DONE]")) {
                        String json = line.substring(5).trim();
                        String chunk = parseSSEChunk(json);
                        if (chunk != null && !chunk.isEmpty()) {
                            fullResponse.append(chunk);
                            try { onChunk.accept(chunk); } catch (Exception e) {
                                log.warn("[AI] 推送回调异常: {}", e.getMessage());
                            }
                        }
                    }
                }
            }

            log.info("[AI] 流式调用完成: 总长度={}", fullResponse.length());
            return fullResponse.toString();

        } catch (RestClientException e) {
            log.error("[AI] 流式调用失败: {}", e.getMessage(), e);
            throw new com.timespace.common.exception.BusinessException(
                    500, "AI生成失败: " + e.getMessage());
        }
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────────

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private ObjectNode makeMessage(String role, String content) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    /** 从同步响应 JSON 中提取 choices[0].message.content */
    private String parseChoiceText(String jsonBody) {
        if (jsonBody == null || jsonBody.isEmpty()) return "";
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode msg = choices.get(0).get("message");
                if (msg != null) {
                    JsonNode content = msg.get("content");
                    return content != null ? content.asText() : "";
                }
            }
        } catch (Exception e) {
            log.warn("[AI] 解析响应 JSON 失败: {}", e.getMessage());
        }
        return "";
    }

    /** 从 SSE data 行解析 delta.content */
    private String parseSSEChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    return content != null ? content.asText() : "";
                }
            }
        } catch (Exception e) {
            log.warn("[AI] 解析 SSE chunk 失败: {}", e.getMessage());
        }
        return "";
    }
}
