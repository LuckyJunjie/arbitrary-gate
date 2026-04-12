package com.timespace.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final StringRedisTemplate redisTemplate;

    private static final String WX_SESSION_KEY_PREFIX = "wx:session:";
    private static final long SESSION_EXPIRE_SECONDS = 3600;

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
        // 3. 写入Redis（开发阶段）
        redisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + phone, code, SMS_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        // TODO: 正式SMS接入 - 调用阿里云SMS API
        // 正式实现参考：
        // DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        // IAcsClient client = new DefaultAcsClient(profile);
        // SendSmsRequest request = new SendSmsRequest();
        // request.setPhoneNumbers(phone);
        // request.setSignName("时光笺");
        // request.setTemplateCode("SMS_xxx");
        // request.setTemplateParam("{\"code\":\"" + code + "\"}");
        // client.getAcsResponse(request);
        log.info("[SMS] 验证码已发送(开发模式), phone={}, code={}", phone, code);
    }

    /**
     * U-02 手机号登录/注册
     * 验证验证码，正确则用户存在则登录，不存在则自动注册（is_guest=0）
     */
    @Transactional
    public WxLoginVO phoneLogin(String phone, String code) {
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
        // 4. 查询用户是否已存在（按手机号）
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
        // 5. 重置每日免费次数
        resetDailyFreeDrawsIfNeeded(user);
        // 6. 签发 Sa-Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        // 7. 构建返回
        WxLoginVO vo = new WxLoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        log.info("[PhoneLogin] 登录成功, phone={}, userId={}", phone, user.getId());
        return vo;
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.length() != 11) return false;
        return phone.matches("^1[3-9]\\d{9}$");
    }

    // ========== 微信相关（原有）============

    /**
     * 调用微信接口用code换session
     * 实际项目中通过Feign或RestTemplate调用微信API
     */
    private WxSessionInfo callWxSessionApi(String code) {
        // TODO: 实际调用 https://api.weixin.qq.com/sns/jscode2session
        // 临时模拟返回
        log.warn("微信Session接口调用模拟，code={}", code);
        WxSessionInfo info = new WxSessionInfo();
        info.setOpenid("mock_openid_" + code);
        info.setSessionKey("mock_session_key_" + code);
        info.setUnionid(null);
        return info;
    }

    /**
     * 解密微信加密数据
     * 实际项目中使用AES-128-CBC解密
     */
    private WxUserInfo decryptWxUserInfo(String sessionKey, String encryptedData, String iv) {
        // TODO: 实际解密
        WxUserInfo info = new WxUserInfo();
        info.setNickname("时光旅人");
        info.setAvatarUrl("https://mmbiz.qpic.cn/mmbiz/fishimg/TJ2aiaZqB0/0");
        return info;
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
