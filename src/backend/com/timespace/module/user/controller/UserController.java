package com.timespace.module.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/user/wx-login
     * 微信登录
     *
     * 请求：
     * {
     *   "code": "微信授权code",
     *   "encryptedData": "加密数据",
     *   "iv": "解密向量"
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "token": "Satoken认证Token",
     *     "user": {
     *       "id": 1,
     *       "nickname": "时光旅人",
     *       "avatarUrl": "https://...",
     *       "inkStone": 100
     *     }
     *   }
     * }
     */
    @PostMapping("/wx-login")
    public Result<WxLoginVO> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        log.info("微信登录请求, code={}", request.getCode());
        WxLoginVO vo = userService.wxLogin(request.getCode(), request.getEncryptedData(), request.getIv());
        return Result.ok(vo);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) throw BusinessException.STORY_NOT_FOUND;
        // 脱敏
        user.setOpenId(null);
        user.setUnionId(null);
        return Result.ok(user);
    }

    /**
     * 更新用户昵称
     */
    @PutMapping("/nickname")
    public Result<Void> updateNickname(@RequestParam String nickname) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.updateNickname(userId, nickname);
        return Result.ok();
    }

    /**
     * U-03 POST /api/user/guest-login
     * 游客登录：生成 UUID 作为 guest_open_id，创建 is_guest=1 的临时用户，签发 token
     */
    @PostMapping("/guest-login")
    public Result<WxLoginVO> guestLogin() {
        log.info("游客登录请求");
        WxLoginVO vo = userService.guestLogin();
        return Result.ok(vo);
    }

    // ========== U-02 手机号登录 ==========

    /**
     * POST /api/user/send-code
     * 发送短信验证码
     *
     * 请求：
     * {
     *   "phone": "13800138000"
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": { "success": true }
     * }
     */
    @PostMapping("/send-code")
    public Result<SendCodeVO> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("发送验证码请求, phone={}", request.getPhone());
        userService.sendSmsCode(request.getPhone());
        SendCodeVO vo = new SendCodeVO();
        vo.setSuccess(true);
        return Result.ok(vo);
    }

    /**
     * POST /api/user/phone-login
     * 手机号一键登录（登录/注册）
     *
     * 请求：
     * {
     *   "phone": "13800138000",
     *   "code": "888888"
     * }
     *
     * 响应：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "token": "Satoken认证Token",
     *     "user": { ... }
     *   }
     * }
     */
    @PostMapping("/phone-login")
    public Result<WxLoginVO> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        log.info("手机号登录请求, phone={}", request.getPhone());
        WxLoginVO vo = userService.phoneLogin(request.getPhone(), request.getCode());
        return Result.ok(vo);
    }

    // ========== DTOs ==========

    @Data
    public static class SendCodeRequest {
        private String phone;
    }

    @Data
    public static class SendCodeVO {
        private boolean success;
    }

    @Data
    public static class PhoneLoginRequest {
        private String phone;
        private String code;
    }

    @Data
    public static class WxLoginRequest {
        private String code;
        private String encryptedData;
        private String iv;
    }

    @Data
    public static class WxLoginVO {
        private String token;
        private UserVO user;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String nickname;
        private String avatarUrl;
        private Integer inkStone;
        private Integer dailyFreeDraws;
        private Integer totalStories;
        private Integer completedStories;
        /** 0=正式用户，1=游客 */
        private Integer isGuest;
    }
}
