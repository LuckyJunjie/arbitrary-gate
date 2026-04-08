package com.timespace.common.utils;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * 指纹工具：用于生成不可猜测的分享码、Token等安全标识
 */
public class FingerprintUtil {

    private static final String SALT = "TimespaceSecretKey2024";

    /**
     * 生成微信SessionKey的指纹（用于数据完整性校验）
     */
    public static String wxSessionFingerprint(String openId, String sessionKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = openId + ":" + sessionKey + ":" + SALT;
            byte[] hash = md.digest(raw.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException("指纹生成失败", e);
        }
    }

    /**
     * 生成合券验证指纹
     * @param storyId 故事ID
     * @param cardId 关键词卡ID
     * @param missingCornerId 缺角卡ID
     */
    public static String jointFingerprint(Long storyId, Long cardId, Long missingCornerId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = storyId + ":" + cardId + ":" + missingCornerId + ":" + SALT;
            byte[] hash = md.digest(raw.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("合券指纹生成失败", e);
        }
    }
}
