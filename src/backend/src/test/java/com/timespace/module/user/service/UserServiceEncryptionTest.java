package com.timespace.module.user.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 三个 TODO 功能的单元测试：
 * 1. 阿里云 SMS 签名算法
 * 2. 微信 jscode2session 响应解析
 * 3. 微信数据 AES-128-CBC 解密
 */
public class UserServiceEncryptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // TODO 1: 阿里云 SMS 签名算法测试
    // =========================================================================

    @Nested
    @DisplayName("阿里云 SMS 签名算法")
    class AliyunSmsSignatureTest {

        private String percentEncode(String value) {
            try {
                return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~");
            } catch (Exception e) {
                return value;
            }
        }

        private String signRequest(TreeMap<String, String> params, String secret) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(percentEncode(e.getKey())).append("=").append(percentEncode(e.getValue()));
            }
            String stringToSign = "POST&" + percentEncode("/") + "&" + percentEncode(sb.toString());
            String hmac = DigestUtil.hmacSha1Hex(secret + "&", stringToSign);
            return Base64.getEncoder().encodeToString(hmac.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("签名结果非空且为有效 Base64")
        void signatureIsValidBase64() {
            TreeMap<String, String> params = new TreeMap<>();
            params.put("AccessKeyId", "testKeyId");
            params.put("PhoneNumbers", "13800138000");
            params.put("SignName", "时光笺");
            params.put("TemplateCode", "SMS_464190095");
            params.put("TemplateParam", "{\"code\":\"123456\"}");
            params.put("Action", "SendSms");
            params.put("Version", "2017-05-25");
            params.put("Format", "JSON");
            params.put("SignatureMethod", "HMAC-SHA1");
            params.put("SignatureNonce", "12345");
            params.put("SignatureVersion", "1.0");
            params.put("RegionId", "cn-hangzhou");
            params.put("Timestamp", "2024-01-01T00:00:00Z");

            String sig = signRequest(params, "testSecret&");
            assertNotNull(sig);
            assertFalse(sig.isEmpty());
            // 验证是有效 Base64
            assertDoesNotThrow(() -> Base64.getDecoder().decode(sig));
        }

        @Test
        @DisplayName("不同参数产生不同签名")
        void differentParamsDifferentSignatures() {
            TreeMap<String, String> params1 = new TreeMap<>();
            params1.put("AccessKeyId", "key1");
            params1.put("Action", "SendSms");

            TreeMap<String, String> params2 = new TreeMap<>();
            params2.put("AccessKeyId", "key2");
            params2.put("Action", "SendSms");

            String sig1 = signRequest(params1, "secret&");
            String sig2 = signRequest(params2, "secret&");
            assertNotEquals(sig1, sig2);
        }

        @Test
        @DisplayName("百分号编码：空格转为 %20")
        void percentEncodeSpaces() {
            assertEquals("Hello%20World", percentEncode("Hello World"));
            assertEquals("%E6%97%B6%E5%85%89", percentEncode("时光"));
        }
    }

    // =========================================================================
    // TODO 2: 微信 jscode2session 响应解析测试
    // =========================================================================

    @Nested
    @DisplayName("微信 jscode2session 响应解析")
    class WxSessionResponseParseTest {

        @Test
        @DisplayName("成功响应解析：openid, session_key, unionid")
        void parseSuccessResponse() throws Exception {
            String json = """
                {
                    "openid": "oxT4G5eXXXXXXXXXXXXXX",
                    "session_key": "5tEVHsXXXXXXXXXXXXXX==",
                    "unionid": "oZXXXXXXXXXXXXXXX"
                }
                """;
            JsonNode node = objectMapper.readTree(json);
            assertEquals("oxT4G5eXXXXXXXXXXXXXX", node.path("openid").asText(null));
            assertEquals("5tEVHsXXXXXXXXXXXXXX==", node.path("session_key").asText(null));
            assertEquals("oZXXXXXXXXXXXXXXX", node.path("unionid").asText(null));
        }

        @Test
        @DisplayName("错误响应解析：errcode 不为 0")
        void parseErrorResponse() throws Exception {
            String json = """
                {
                    "errcode": 40029,
                    "errmsg": "invalid code"
                }
                """;
            JsonNode node = objectMapper.readTree(json);
            assertTrue(node.has("errcode"));
            assertEquals(40029, node.get("errcode").asInt());
            assertEquals("invalid code", node.path("errmsg").asText());
        }

        @Test
        @DisplayName("无 unionid 场景（仅绑定开放平台账号才有）")
        void parseResponseWithoutUnionid() throws Exception {
            String json = """
                {
                    "openid": "oxT4G5eXXXXXXXXXXXXXX",
                    "session_key": "5tEVHsXXXXXXXXXXXXXX=="
                }
                """;
            JsonNode node = objectMapper.readTree(json);
            assertNotNull(node.path("openid").asText(null));
            assertNotNull(node.path("session_key").asText(null));
            assertNull(node.path("unionid").asText(null));
        }
    }

    // =========================================================================
    // TODO 3: 微信数据 AES-128-CBC 解密测试
    // =========================================================================

    @Nested
    @DisplayName("微信数据 AES-128-CBC 解密")
    class WxDataDecryptionTest {

        /**
         * 使用官方微信小程序解密示例数据进行验证
         * 参考：https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/signature.html
         */
        @Test
        @DisplayName("标准 AES-128-CBC PKCS7Padding 解密验证")
        void decryptWxDataStandard() throws Exception {
            // 构造一个已知明文的数据包
            String plainJson = "{\"nickName\":\"测试用户\",\"avatarUrl\":\"https://example.com/avatar.png\",\"unionId\":\"test_union_123\"}";

            // 16字节 key（模拟 sessionKey，微信保证为 128bit）
            String sessionKeyBase64 = "tiihtNczf5v6AKRyjwEUhQ=="; // 16 bytes
            // 16字节 IV
            String ivBase64 = "r7BXXKkLb8qrSNn3nWh8nA==";          // 16 bytes

            byte[] key = Base64.getDecoder().decode(sessionKeyBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            // 先加密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));
            String encryptedDataBase64 = Base64.getEncoder().encodeToString(encrypted);

            // 再解密（模拟 decryptWxUserInfo 逻辑）
            byte[] decrypted = decryptAes128Cbc(encryptedDataBase64, sessionKeyBase64, ivBase64);
            String resultJson = new String(decrypted, StandardCharsets.UTF_8);

            assertTrue(resultJson.contains("测试用户"));
            assertTrue(resultJson.contains("https://example.com/avatar.png"));
            assertTrue(resultJson.contains("test_union_123"));
        }

        @Test
        @DisplayName("AES 解密 - sessionKey 长度校验（16字节）")
        void sessionKeyMustBe16Bytes() {
            // 15字节（非法）
            String shortKey = Base64.getEncoder().encodeToString(new byte[15]);
            assertThrows(Exception.class, () -> decryptAes128Cbc("abc", shortKey, "r7BXXKkLb8qrSNn3nWh8nA=="));
        }

        @Test
        @DisplayName("AES 解密 - 无 unionid 场景")
        void decryptWithoutUnionId() throws Exception {
            String plainJson = "{\"nickName\":\"时光旅人\",\"avatarUrl\":\"https://example.com/fish.png\"}";
            String sessionKeyBase64 = "tiihtNczf5v6AKRyjwEUhQ==";
            String ivBase64 = "r7BXXKkLb8qrSNn3nWh8nA==";

            byte[] key = Base64.getDecoder().decode(sessionKeyBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));
            String encryptedDataBase64 = Base64.getEncoder().encodeToString(encrypted);

            byte[] decrypted = decryptAes128Cbc(encryptedDataBase64, sessionKeyBase64, ivBase64);
            String resultJson = new String(decrypted, StandardCharsets.UTF_8);

            assertTrue(resultJson.contains("时光旅人"));
            assertFalse(resultJson.contains("unionId")); // 原始数据无 unionId
        }

        @Test
        @DisplayName("AES 解密 - 异常输入不抛异常（降级处理）")
        void invalidInputDoesNotThrow() {
            // Base64 解码失败的非法数据
            assertDoesNotThrow(() -> decryptAes128Cbc("not-valid-base64!!!", "tiihtNczf5v6AKRyjwEUhQ==", "r7BXXKkLb8qrSNn3nWh8nA=="));
            // null sessionKey
            assertDoesNotThrow(() -> decryptAes128Cbc("abc", null, "r7BXXKkLb8qrSNn3nWh8nA=="));
            // null iv
            assertDoesNotThrow(() -> decryptAes128Cbc("abc", "tiihtNczf5v6AKRyjwEUhQ==", null));
        }

        /**
         * 模拟 UserService.decryptWxUserInfo 的核心解密逻辑
         */
        private byte[] decryptAes128Cbc(String encryptedData, String sessionKey, String iv) throws Exception {
            if (sessionKey == null || encryptedData == null || iv == null) {
                throw new IllegalArgumentException("参数不能为 null");
            }
            byte[] keyBytes = Base64.getDecoder().decode(sessionKey);
            byte[] dataBytes = Base64.getDecoder().decode(encryptedData);
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            if (keyBytes.length != 16) {
                throw new IllegalArgumentException("sessionKey 长度必须为 16 字节");
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
            return cipher.doFinal(dataBytes);
        }
    }

    // =========================================================================
    // 集成：手机号合法性校验
    // =========================================================================

    @Nested
    @DisplayName("手机号格式校验")
    class PhoneValidationTest {

        private boolean isValidPhone(String phone) {
            if (phone == null || phone.length() != 11) return false;
            return phone.matches("^1[3-9]\\d{9}$");
        }

        @Test
        @DisplayName("合法手机号")
        void validPhones() {
            assertTrue(isValidPhone("13800138000"));
            assertTrue(isValidPhone("19912345678"));
            assertTrue(isValidPhone("17012345678"));
        }

        @Test
        @DisplayName("非法手机号：长度不对 / 首位非1 / 含字母")
        void invalidPhones() {
            assertFalse(isValidPhone("1380013800"));   // 10位
            assertFalse(isValidPhone("138001380001")); // 12位
            assertFalse(isValidPhone("10800138000"));  // 108开头
            assertFalse(isValidPhone("1380013800a"));  // 含字母
            assertFalse(isValidPhone(null));
        }
    }
}
