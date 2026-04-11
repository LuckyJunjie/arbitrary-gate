package com.timespace.module.wechat.controller;

import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.wechat.service.WeChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SH-05 微信 JSSDK 控制器
 * <p>
 * 提供 JSSDK 签名接口，供前端页面在微信浏览器中初始化 JSSDK。
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat")
@RequiredArgsConstructor
public class WeChatController {

    private final WeChatService weChatService;

    /**
     * GET /api/wechat/jsapi/config
     * <p>
     * 获取微信 JSSDK 签名配置参数。
     * 前端调用 wx.config(...) 时需要此接口返回的参数。
     *
     * @param url 当前页面 URL（例：https://example.com/share/ABC123）
     *            注意：必须是调用 wx.config 的页面 URL，
     *            且域名需在微信公众号后台已配置 JS 安全域名。
     * @return JSSDK config 对象 { appId, timestamp, nonceStr, signature }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "data": {
     *     "appId": "wx1234567890abcdef",
     *     "timestamp": 1712800000,
     *     "nonceStr": "abc123def456",
     *     "signature": "e2b3e8c1d..."
     *   }
     * }
     */
    @GetMapping("/jsapi/config")
    public Result<Map<String, String>> getJsapiConfig(@RequestParam String url) {
        log.info("JSSDK config 请求: url={}", url);
        Map<String, String> config = weChatService.buildJsapiConfig(url);
        return Result.ok(config);
    }
}
