package com.timespace.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AI 内容安全检测器
 *
 * 支持两种模式：
 * 1. 阿里云内容安全 API（green.cn-shanghai.aliyuncs.com）- 需要配置 CONTENT_SAFETY_API_KEY
 *    格式：AccessKeyId:AccessKeySecret
 * 2. 关键词黑名单过滤（兜底，无需配置）
 *
 * 检测类型：色情、暴力、政治敏感
 */
@Slf4j
@Component
public class ContentSafetyChecker {

    private static final String ALIYUN_GREEN_HOST = "green.cn-shanghai.aliyuncs.com";
    private static final String TEXT_SCAN_PATH = "/text/v4/scan";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 关键词黑名单（兜底方案）────────────────────────────────────────────────
    // 检测类别：色情 | 暴力 | 政治敏感 | 其他违法
    private static final List<KeywordRule> BLACKLIST_RULES = List.of(
            new KeywordRule(Pattern.compile("色情|淫秽|黄色网站|成人视频|一夜情|援交|买春|约炮"), "色情内容"),
            new KeywordRule(Pattern.compile("杀人|分尸|凌迟|酷刑|虐杀|爆炸物|制造炸弹|枪支|弹药"), "暴力内容"),
            new KeywordRule(Pattern.compile("颠覆国家|分裂国土|反动言论|非法集会|暴恐"), "政治敏感"),
            new KeywordRule(Pattern.compile("赌博|诈骗|吸毒|贩毒|走私军火"), "违法内容")
    );

    // ── 配置 ─────────────────────────────────────────────────────────────────
    @Value("${spring.content-safety.api-key:}")
    private String apiKey;

    @Value("${spring.content-safety.enabled:true}")
    private boolean safetyEnabled;

    // ── 对外接口 ─────────────────────────────────────────────────────────────

    /**
     * 检测文本安全性
     *
     * @param text 待检测文本
     * @return 检测结果
     *         - safe=true:  内容安全
     *         - safe=false: 内容违规，reason 为违规原因
     */
    public SafetyResult check(String text) {
        if (text == null || text.isBlank()) {
            return new SafetyResult(true, null);
        }

        if (!safetyEnabled) {
            log.debug("[ContentSafety] 安全检测已禁用，跳过");
            return new SafetyResult(true, null);
        }

        log.debug("[ContentSafety] 开始检测，长度={}", text.length());

        // 优先使用阿里云 API（如果配置了 api-key）
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                SafetyResult result = checkWithAliyun(text);
                log.info("[ContentSafety] 阿里云检测结果: {}", result);
                return result;
            } catch (Exception e) {
                log.warn("[ContentSafety] 阿里云API调用失败，降级到关键词黑名单: {}", e.getMessage());
            }
        }

        // 关键词黑名单检测（兜底）
        return checkWithBlacklist(text);
    }

    /**
     * 检测并自动重试（最多 maxRetries 次）
     *
     * @param text          待检测文本
     * @param maxRetries    最大重试次数（含首次，即 maxRetries=3 时最多生成3次）
     * @param regenerateFn  不通过时重新生成的回调，返回新文本
     * @return 最终检测结果
     */
    public SafetyResult checkWithRetry(String text, int maxRetries,
                                        java.util.function.Supplier<String> regenerateFn) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            SafetyResult result = check(text);
            if (result.isSafe()) {
                log.info("[ContentSafety] 第{}次检测通过", attempt);
                return result;
            }
            log.warn("[ContentSafety] 第{}次检测不通过: {}，尝试重新生成...", attempt, result.getReason());

            if (attempt < maxRetries && regenerateFn != null) {
                String newText = regenerateFn.get();
                if (newText != null && !newText.isBlank()) {
                    text = newText;
                }
            }
        }

        // 所有尝试都不通过
        log.error("[ContentSafety] 内容安全检测{}次均不通过", maxRetries);
        return new SafetyResult(false, "内容安全检测未通过");
    }

    // ── 阿里云内容安全 API ───────────────────────────────────────────────────

    /**
     * 阿里云文本内容安全同步检测
     * API: POST https://green.cn-shanghai.aliyuncs.com/text/v4/scan
     */
    private SafetyResult checkWithAliyun(String text) throws Exception {
        String ak = extractAccessKey(apiKey);
        String aks = extractAccessKeySecret(apiKey);

        long timestamp = System.currentTimeMillis();

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("Service", "open_api");
        requestBody.put("ServiceVersion", "2018-05-09");

        ObjectNode task = objectMapper.createObjectNode();
        task.put("DataId", UUID.randomUUID().toString());
        task.put("Content", text);

        // 配置检测场景
        ArrayNode features = objectMapper.createArrayNode();
        features.add(buildFeature("porn", "multi", "二次元色情,色情"));
        features.add(buildFeature("violence", "multi", "警察军事,暴恐打架"));
        features.add(buildFeature("politics", "multi", "涉政"));
        task.set("Features", features);

        task.putArray("Type").add("text");
        requestBody.putArray("Tasks").add(task);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // 签名: HMAC-SHA1(AccessKeySecret + timestamp)
        String signature = computeSignature(aks, timestamp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-acs-signature", signature);
        headers.set("X-acs-signature-version", "1.0");
        headers.set("X-acs-signature-method", "HMAC-SHA1");
        headers.set("X-acs-access-key-id", ak);
        headers.set("X-acs-request-id", UUID.randomUUID().toString());
        headers.set("Host", ALIYUN_GREEN_HOST);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        String url = "https://" + ALIYUN_GREEN_HOST + TEXT_SCAN_PATH;

        var resp = restTemplate.postForEntity(url, entity, String.class);
        return parseAliyunResponse(resp.getBody(), text);
    }

    private JsonNode buildFeature(String name, String selected, String arguments) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("Name", name);
        node.put("Selected", selected);
        node.put("Arguments", arguments);
        return node;
    }

    private SafetyResult parseAliyunResponse(String body, String originalText) {
        if (body == null || body.isBlank()) {
            return checkWithBlacklist(originalText);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("Data");
            if (data == null) {
                return checkWithBlacklist(originalText);
            }

            JsonNode results = data.get("Results");
            if (results == null || !results.isArray()) {
                return checkWithBlacklist(originalText);
            }

            for (JsonNode result : results) {
                JsonNode labels = result.get("Labels");
                if (labels != null && labels.isArray()) {
                    for (JsonNode label : labels) {
                        String labelName = label.path("Name").asText("");
                        String suggestion = label.path("Suggestion").asText("pass");

                        if ("pass".equalsIgnoreCase(suggestion)) {
                            continue;
                        }

                        String severity = label.path("Severity").asText("medium");
                        return new SafetyResult(false,
                                String.format("内容违规[%s]: %s", severity, labelName));
                    }
                }
            }

            return new SafetyResult(true, null);

        } catch (Exception e) {
            log.warn("[ContentSafety] 解析阿里云响应失败: {}, 降级到黑名单", e.getMessage());
            return checkWithBlacklist(originalText);
        }
    }

    // ── 关键词黑名单（兜底）────────────────────────────────────────────────────

    private SafetyResult checkWithBlacklist(String text) {
        for (KeywordRule rule : BLACKLIST_RULES) {
            var matcher = rule.pattern.matcher(text);
            if (matcher.find()) {
                String matched = matcher.group();
                log.warn("[ContentSafety] 黑名单命中: category={}, keyword={}", rule.category, matched);
                return new SafetyResult(false, rule.category);
            }
        }
        return new SafetyResult(true, null);
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────────

    /**
     * 解析 AccessKeyId（格式: AccessKeyId:AccessKeySecret）
     */
    private String extractAccessKey(String apiKeyValue) {
        if (apiKeyValue == null || !apiKeyValue.contains(":")) {
            return "";
        }
        return apiKeyValue.substring(0, apiKeyValue.indexOf(":"));
    }

    /**
     * 解析 AccessKeySecret（格式: AccessKeyId:AccessKeySecret）
     */
    private String extractAccessKeySecret(String apiKeyValue) {
        if (apiKeyValue == null || !apiKeyValue.contains(":")) {
            return apiKeyValue != null ? apiKeyValue : "";
        }
        return apiKeyValue.substring(apiKeyValue.indexOf(":") + 1);
    }

    /**
     * HMAC-SHA1 签名
     */
    private String computeSignature(String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            log.error("[ContentSafety] 签名计算失败: {}", e.getMessage());
            return "";
        }
    }

    // ── 内部类 ───────────────────────────────────────────────────────────────

    private record KeywordRule(Pattern pattern, String category) {
    }

    /**
     * 检测结果
     *
     * @param safe   是否安全
     * @param reason 违规原因（仅 safe=false 时有值）
     */
    public record SafetyResult(boolean safe, String reason) {

        public boolean isSafe() {
            return safe;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return safe ? "SafetyResult{safe=true}" : "SafetyResult{safe=false, reason='" + reason + "'}";
        }
    }
}
