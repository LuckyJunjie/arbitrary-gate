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
    }
}
