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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void consumeInkStone(long userId, int amount) {
        User user = getById(userId);
        if (user.getInkStone() < amount) throw BusinessException.INK_STONE_NOT_ENOUGH;
        user.setInkStone(user.getInkStone() - amount);
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
        save(user);
        return user;
    }

    private void resetDailyFreeDrawsIfNeeded(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReset = user.getLastFreeResetTime();
        if (lastReset == null || lastReset.toLocalDate().isBefore(now.toLocalDate())) {
            user.setDailyFreeDraws(1);
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
        return vo;
    }

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
