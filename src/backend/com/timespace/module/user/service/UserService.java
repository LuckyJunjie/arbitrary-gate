package com.timespace.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.FingerprintUtil;
import com.timespace.module.user.controller.UserController.UserVO;
import com.timespace.module.user.controller.UserController.WxLoginVO;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final StringRedisTemplate redisTemplate;
    private final com.timespace.module.card.mapper.UserKeywordCardMapper userKeywordCardMapper;
    private final com.timespace.module.card.mapper.UserEventCardMapper userEventCardMapper;
    private final com.timespace.module.story.mapper.StoryMapper storyMapper;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String WX_SESSION_KEY_PREFIX = "wx:session:";
    private static final long SESSION_EXPIRE_SECONDS = 3600;

    // 阿里云 SMS 配置
    @Value("${spring.sms.access-key-id:}")
    private String smsAccessKeyId;

    @Value("${spring.sms.access-key-secret:}")
    private String smsAccessKeySecret;

    @Value("${spring.sms.sign-name:时光笺}")
    private String smsSignName;

    @Value("${spring.sms.template-code:SMS_464190095}")
    private String smsTemplateCode;

    @Value("${spring.sms.endpoint:https://dysmsapi.aliyuncs.com}")
    private String smsEndpoint;

    // 微信配置
    @Value("${timespace.wechat.app-id:}")
    private String wxAppId;

    @Value("${timespace.wechat.app-secret:}")
    private String wxAppSecret;

    /** 游客每日免费抽卡次数上限（正式用户 3 次，游客 1 次） */
    private static final int GUEST_DAILY_FREE_COUNT = 1;

    @Value("${timespace.card.daily-free-count:3}")
    private int dailyFreeCount;

    /**
     * 微信登录流程：
     * 1. 用code调用微信接口获取session_key + openid
     * 2. 解析encryptedData获取用户信息
     * 3. 查询/创建用户记录
     * 4. 生成Sa-Token并返回
     */
    @Transactional
    public WxLoginVO wxLogin(String code, String encryptedData, String iv) {
        // 1. 调用微信接口获取session信息
        WxSessionInfo sessionInfo = callWxSessionApi(code);

        // 2. 解析用户数据（encryptedData解密，需要session_key）
        WxUserInfo wxUserInfo = null;
        if (encryptedData != null && iv != null) {
            wxUserInfo = decryptWxUserInfo(sessionInfo.getSessionKey(), encryptedData, iv);
        }

        // 3. 查询或创建用户
        User user = findOrCreateUser(wxUserInfo, sessionInfo.getOpenid());

        // 4. 重置每日免费次数（跨天后自动重置）
        resetDailyFreeDrawsIfNeeded(user);

        // 5. 生成Sa-Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // 6. 缓存微信Session（用于前端数据校验）
        String fingerprint = FingerprintUtil.wxSessionFingerprint(sessionInfo.getOpenid(), sessionInfo.getSessionKey());
        redisTemplate.opsForValue().set(WX_SESSION_KEY_PREFIX + fingerprint,
                sessionInfo.getSessionKey(), SESSION_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 7. 构建返回
        WxLoginVO vo = new WxLoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        log.info("用户登录成功: userId={}, nickname={}", user.getId(), user.getNickname());
        return vo;
    }

    public void updateNickname(long userId, String nickname) {
        User user = getById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        user.setNickname(nickname);
        updateById(user);
    }

    /**
     * U-03 游客登录
     * 生成 UUID 作为 guest_open_id，创建 is_guest=1 的临时用户
     * 游客每日免费抽 1 次（正式用户 3 次）
     */
    @Transactional
    public WxLoginVO guestLogin() {
        String guestOpenId = "guest_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String guestDeviceId = java.util.UUID.randomUUID().toString().replace("-", "");
        // 昵称：旅人 + 随机 4 位数字（如"旅人7293"）
        String guestNickname = "旅人" + RandomUtil.randomInt(1000, 9999);

        User user = new User();
        user.setOpenId(guestOpenId);
        user.setGuestDeviceId(guestDeviceId);
        user.setNickname(guestNickname);
        user.setInkStone(0);
        user.setDailyFreeDraws(GUEST_DAILY_FREE_COUNT);
        user.setLastFreeResetTime(LocalDateTime.now());
        user.setTotalStories(0);
        user.setCompletedStories(0);
        user.setIsGuest(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        save(user);

        // 重置每日免费次数（跨天后自动重置为 1）
        resetDailyFreeDrawsIfNeeded(user);

        // 生成 Sa-Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        WxLoginVO vo = new WxLoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        log.info("游客登录成功: userId={}, nickname={}", user.getId(), user.getNickname());
        return vo;
    }

    /**
     * 判断用户是否为游客
     */
    public boolean isGuest(Long userId) {
        User user = getById(userId);
        return user != null && user.getIsGuest() != null && user.getIsGuest() == 1;
    }

    public void consumeInkStone(long userId, int amount) {
        User user = getById(userId);
        if (user.getInkStone() < amount) throw BusinessException.INK_STONE_NOT_ENOUGH;
        user.setInkStone(user.getInkStone() - amount);
        updateById(user);
    }

    public boolean hasDailyFreeDraw(Long userId) {
        User user = getById(userId);
        if (user == null) return false;
        resetDailyFreeDrawsIfNeeded(user);
        return user.getDailyFreeDraws() != null && user.getDailyFreeDraws() > 0;
    }

    /**
     * 获取用户今日免费抽卡次数（来自DB，不含Redis额外次数）
     */
    public int getDailyFreeDraws(Long userId) {
        User user = getById(userId);
        if (user == null) return 0;
        resetDailyFreeDrawsIfNeeded(user);
        return user.getDailyFreeDraws() != null ? user.getDailyFreeDraws() : 0;
    }

    public void useDailyFreeDraw(Long userId) {
        User user = getById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        resetDailyFreeDrawsIfNeeded(user);
        if (user.getDailyFreeDraws() == null || user.getDailyFreeDraws() <= 0) {
            throw BusinessException.DAILY_FREE_EXHAUSTED;
        }
        user.setDailyFreeDraws(user.getDailyFreeDraws() - 1);
        updateById(user);
    }

    public void addInkStone(long userId, int amount) {
        User user = getById(userId);
        user.setInkStone(user.getInkStone() + amount);
        updateById(user);
    }

    private User findOrCreateUser(WxUserInfo wxUserInfo, String openid) {
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenId, openid));
        if (user != null) {
            // 更新用户信息
            if (wxUserInfo != null) {
                if (wxUserInfo.getNickname() != null) user.setNickname(wxUserInfo.getNickname());
                if (wxUserInfo.getAvatarUrl() != null) user.setAvatarUrl(wxUserInfo.getAvatarUrl());
                if (wxUserInfo.getUnionId() != null) user.setUnionId(wxUserInfo.getUnionId());
            }
            updateById(user);
            return user;
        }
        // 创建新用户
        user = new User();
        user.setOpenId(openid);
        if (wxUserInfo != null) {
            user.setNickname(wxUserInfo.getNickname() != null ? wxUserInfo.getNickname() : "时光旅人");
            user.setAvatarUrl(wxUserInfo.getAvatarUrl());
            user.setUnionId(wxUserInfo.getUnionId());
        } else {
            user.setNickname("时光旅人");
        }
        user.setInkStone(100); // 新用户赠送100墨晶
        user.setDailyFreeDraws(1);
        user.setLastFreeResetTime(LocalDateTime.now());
        user.setTotalStories(0);
        user.setCompletedStories(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsGuest(0);
        save(user);
        return user;
    }

    private void resetDailyFreeDrawsIfNeeded(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReset = user.getLastFreeResetTime();
        if (lastReset == null || lastReset.toLocalDate().isBefore(now.toLocalDate())) {
            int dailyLimit = (user.getIsGuest() != null && user.getIsGuest() == 1)
                    ? GUEST_DAILY_FREE_COUNT
                    : dailyFreeCount;
            user.setDailyFreeDraws(dailyLimit);
            user.setLastFreeResetTime(now);
            updateById(user);
        }
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setInkStone(user.getInkStone());
        vo.setDailyFreeDraws(user.getDailyFreeDraws());
        vo.setTotalStories(user.getTotalStories());
        vo.setCompletedStories(user.getCompletedStories());
        vo.setIsGuest(user.getIsGuest());
        vo.setGuestDeviceId(user.getGuestDeviceId());
        return vo;
    }

    // ========== U-02 手机号登录 ==========

    private static final String SMS_CODE_KEY_PREFIX = "sms:code:";
    private static final long SMS_CODE_EXPIRE_SECONDS = 300; // 5分钟

    /**
     * U-02 发送短信验证码
     * 开发阶段：验证码写入Redis，key=phone，实际调用时用万能码 888888
     * 正式阶段：调用阿里云SMS API (dysmsapi.aliyuncs.com)
     */
    public void sendSmsCode(String phone) {
        // 1. 验证手机号格式
        if (!isValidPhone(phone)) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        // 2. 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 写入Redis（开发阶段 - 用于本地验证码校验）
        redisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + phone, code, SMS_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        // 4. 调用阿里云SMS API 发送真实短信
        try {
            sendSmsViaAliyun(phone, code);
            log.info("[SMS] 验证码已发送, phone={}, code={}", phone, code);
        } catch (Exception e) {
            log.error("[SMS] 短信发送失败, phone={}, error={}", phone, e.getMessage(), e);
            throw BusinessException.SMS_CODE_SEND_FAILED;
        }
    }

    /**
     * 通过阿里云 REST API 发送短信（不依赖 SDK）
     * API 文档：https://help.aliyun.com/zh/sms/developer-reference/sendSms
     */
    private void sendSmsViaAliyun(String phone, String code) {
        String regionId = "cn-hangzhou";
        // 公共参数
        Map<String, String> params = new LinkedHashMap<>();
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", String.valueOf(System.nanoTime()));
        params.put("AccessKeyId", smsAccessKeyId);
        params.put("Timestamp", formatIso8601Date());
        params.put("Format", "JSON");
        params.put("Action", "SendSms");
        params.put("Version", "2017-05-25");
        params.put("RegionId", regionId);
        params.put("PhoneNumbers", phone);
        params.put("SignName", smsSignName);
        params.put("TemplateCode", smsTemplateCode);
        params.put("TemplateParam", "{\"code\":\"" + code + "\"}");

        // 生成签名
        String signature = signRequest(params, smsAccessKeySecret);
        params.put("Signature", signature);

        // 发送请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = buildFormBody(params);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        String resp = restTemplate.postForObject(smsEndpoint, entity, String.class);
        log.debug("[SMS] Aliyun response: {}", resp);

        if (resp != null) {
            try {
                JsonNode node = objectMapper.readTree(resp);
                String code_ = node.path("Code").asText();
                if (!"OK".equals(code_)) {
                    log.warn("[SMS] Aliyun 返回异常: Code={}, Message={}", code_, node.path("Message").asText());
                    throw new BusinessException(500, "短信发送失败: " + node.path("Message").asText());
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[SMS] 解析响应异常（仍视为成功）: {}", e.getMessage());
            }
        }
    }

    /** 签名算法：HTTPS FormatQueryString + HMAC-SHA1 + Base64 */
    private String signRequest(Map<String, String> params, String secret) {
        // 按字典序排序参数
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(percentEncode(e.getKey())).append("=").append(percentEncode(e.getValue()));
        }
        String stringToSign = "POST&" + percentEncode("/") + "&" + percentEncode(sb.toString());
        String hmac = DigestUtil.hmacSha1Hex(secret + "&", stringToSign);
        return Base64.getEncoder().encodeToString(hmac.getBytes(StandardCharsets.UTF_8));
    }

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

    private String formatIso8601Date() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        return now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(percentEncode(e.getKey())).append("=").append(percentEncode(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * U-02 手机号登录/注册
     * 验证验证码，正确则用户存在则登录，不存在则自动注册（is_guest=0）
     */
    @Transactional
    public WxLoginVO phoneLogin(String phone, String code, String guestDeviceId) {
        // 1. 验证手机号格式
        if (!isValidPhone(phone)) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        // 2. 校验验证码（万能码 888888 也接受）
        String storedCode = redisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + phone);
        if (storedCode == null || (!storedCode.equals(code) && !"888888".equals(code))) {
            throw BusinessException.SMS_CODE_INVALID;
        }
        // 3. 验证通过后删除验证码（一次性）
        redisTemplate.delete(SMS_CODE_KEY_PREFIX + phone);

        // 4. 查找关联的游客账号（如果有 guestDeviceId）
        User guestUser = null;
        if (guestDeviceId != null && !guestDeviceId.isBlank()) {
            guestUser = getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getGuestDeviceId, guestDeviceId)
                    .eq(User::getIsGuest, 1));
            if (guestUser != null) {
                log.info("[PhoneLogin] 找到关联游客账号, guestUserId={}, guestDeviceId={}", guestUser.getId(), guestDeviceId);
            }
        }

        // 5. 查询正式用户是否已存在（按手机号）
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            // 不存在则自动注册
            user = new User();
            user.setPhone(phone);
            user.setNickname("旅人" + RandomUtil.randomInt(1000, 9999));
            user.setInkStone(100); // 新用户赠送100墨晶
            user.setDailyFreeDraws(dailyFreeCount);
            user.setLastFreeResetTime(LocalDateTime.now());
            user.setTotalStories(0);
            user.setCompletedStories(0);
            user.setIsGuest(0); // 正式用户
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            // 如果有关联游客：将游客的墨晶、故事数等合并过来
            if (guestUser != null) {
                user.setInkStone(user.getInkStone() + (guestUser.getInkStone() != null ? guestUser.getInkStone() : 0));
                user.setTotalStories(guestUser.getTotalStories() != null ? guestUser.getTotalStories() : 0);
                user.setCompletedStories(guestUser.getCompletedStories() != null ? guestUser.getCompletedStories() : 0);
            }
            save(user);
            log.info("[PhoneLogin] 新用户注册, phone={}, userId={}", phone, user.getId());
        } else {
            // 已存在则检查是否为游客（升级账号）
            if (user.getIsGuest() != null && user.getIsGuest() == 1) {
                user.setIsGuest(0); // 游客升级为正式用户
                user.setUpdatedAt(LocalDateTime.now());
                updateById(user);
                log.info("[PhoneLogin] 游客账号升级, phone={}, userId={}", phone, user.getId());
            }
        }

        // 6. 迁移游客数据（关键词卡、事件卡、故事等）到正式账号
        if (guestUser != null && !guestUser.getId().equals(user.getId())) {
            migrateGuestData(guestUser.getId(), user.getId());
            // 删除游客账号记录
            removeById(guestUser.getId());
            log.info("[PhoneLogin] 游客数据已迁移并删除 guestUserId={} -> userId={}", guestUser.getId(), user.getId());
        }

        // 7. 重置每日免费次数
        resetDailyFreeDrawsIfNeeded(user);
        // 8. 签发 Sa-Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        // 9. 构建返回
        WxLoginVO vo = new WxLoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        log.info("[PhoneLogin] 登录成功, phone={}, userId={}", phone, user.getId());
        return vo;
    }

    /**
     * U-03 将游客的所有数据迁移到正式账号
     * 迁移内容：关键词卡、事件卡、故事记录、成就等
     */
    private void migrateGuestData(Long guestUserId, Long formalUserId) {
        // 迁移关键词卡（user_keyword_card）
        var keywordWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.timespace.module.card.entity.UserKeywordCard>()
                .eq(com.timespace.module.card.entity.UserKeywordCard::getUserId, guestUserId)
                .set(com.timespace.module.card.entity.UserKeywordCard::getUserId, formalUserId);
        userKeywordCardMapper.update(null, keywordWrapper);

        // 迁移事件卡（user_event_card）
        var eventWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.timespace.module.card.entity.UserEventCard>()
                .eq(com.timespace.module.card.entity.UserEventCard::getUserId, guestUserId)
                .set(com.timespace.module.card.entity.UserEventCard::getUserId, formalUserId);
        userEventCardMapper.update(null, eventWrapper);

        // 迁移故事（story 表 - 将 story 的 user_id 从 guest 改为 formal）
        var storyWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.timespace.module.story.entity.Story>()
                .eq(com.timespace.module.story.entity.Story::getUserId, guestUserId)
                .set(com.timespace.module.story.entity.Story::getUserId, formalUserId);
        storyMapper.update(null, storyWrapper);

        log.info("[Migrate] guestUserId={} -> formalUserId={} 数据迁移完成", guestUserId, formalUserId);
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.length() != 11) return false;
        return phone.matches("^1[3-9]\\d{9}$");
    }

    // ========== 微信相关（原有）============

    /**
     * 调用微信接口用code换session
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     */
    private WxSessionInfo callWxSessionApi(String code) {
        if (wxAppId == null || wxAppId.isBlank() || wxAppSecret == null || wxAppSecret.isBlank()) {
            log.warn("[Wx] 微信 AppId 或 AppSecret 未配置，使用模拟返回");
            WxSessionInfo info = new WxSessionInfo();
            info.setOpenid("mock_openid_" + code);
            info.setSessionKey("mock_session_key_" + code);
            info.setUnionid(null);
            return info;
        }

        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wxAppId, wxAppSecret, code);

        try {
            String resp = restTemplate.getForObject(url, String.class);
            log.debug("[Wx] jscode2session response: {}", resp);
            JsonNode node = objectMapper.readTree(resp);

            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.error("[Wx] jscode2session 失败: errcode={}, errmsg={}",
                        node.get("errcode").asInt(), node.path("errmsg").asText());
                throw new BusinessException(400, "微信登录失败: " + node.path("errmsg").asText());
            }

            WxSessionInfo info = new WxSessionInfo();
            info.setOpenid(node.path("openid").asText(null));
            info.setSessionKey(node.path("session_key").asText(null));
            info.setUnionid(node.path("unionid").asText(null));
            log.info("[Wx] 微信Session获取成功, openid={}", info.getOpenid());
            return info;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Wx] jscode2session 异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "微信登录异常，请稍后重试");
        }
    }

    /**
     * 解密微信加密数据
     * 微信小程序用户数据解密：https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/signature.html
     * 算法：AES-128-CBC，PKCS7Padding
     *
     * @param sessionKey     微信 session_key（Base64 编码）
     * @param encryptedData  加密数据（Base64 编码）
     * @param iv             初始向量（Base64 编码）
     */
    private WxUserInfo decryptWxUserInfo(String sessionKey, String encryptedData, String iv) {
        if (sessionKey == null || encryptedData == null || iv == null) {
            log.warn("[Wx] 解密参数缺失, sessionKey={}, encryptedData={}, iv={}",
                    sessionKey != null, encryptedData != null, iv != null);
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(sessionKey);
            byte[] dataBytes = Base64.getDecoder().decode(encryptedData);
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            // 校验数据长度（AES-128 = 16字节 key, 16字节 IV）
            if (keyBytes.length != 16) {
                log.warn("[Wx] sessionKey 长度非法: {}", keyBytes.length);
                throw new BusinessException(400, "微信数据解密失败：session_key 无效");
            }

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(dataBytes);

            // 解密结果 = { "nickName": "...", "avatarUrl": "...", ... }
            String jsonStr = new String(decrypted, StandardCharsets.UTF_8);
            log.debug("[Wx] 解密原始数据: {}", jsonStr);
            JsonNode node = objectMapper.readTree(jsonStr);

            WxUserInfo info = new WxUserInfo();
            info.setNickname(node.path("nickName").asText(null));
            info.setAvatarUrl(node.path("avatarUrl").asText(null));
            info.setUnionId(node.path("unionId").asText(null));
            log.info("[Wx] 用户数据解密成功, nickname={}", info.getNickname());
            return info;

        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("[Wx] Base64 解码失败: {}", e.getMessage());
            throw new BusinessException(400, "微信数据解密失败：数据格式错误");
        } catch (Exception e) {
            log.error("[Wx] 微信数据解密异常: {}", e.getMessage(), e);
            // 解密失败不影响登录流程，降级返回 null
            return null;
        }
    }

    @Data
    static class WxSessionInfo {
        private String openid;
        private String sessionKey;
        private String unionid;
    }

    @Data
    static class WxUserInfo {
        private String nickname;
        private String avatarUrl;
        private String unionId;
    }
}
