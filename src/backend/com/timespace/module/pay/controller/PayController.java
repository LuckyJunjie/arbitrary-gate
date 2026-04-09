package com.timespace.module.pay.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.timespace.common.exception.GlobalExceptionHandler.Result;
import com.timespace.module.pay.dto.CreateOrderRequest;
import com.timespace.module.pay.dto.CreateOrderResponse;
import com.timespace.module.pay.service.PayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * POST /api/pay/create-order
     * 创建订单并获取微信支付参数
     */
    @PostMapping("/create-order")
    public Result<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        log.info("创建订单: userId={}, packageId={}", userId, request.getPackageId());
        CreateOrderResponse resp = payService.createOrder(userId, request);
        return Result.ok(resp);
    }

    /**
     * POST /api/pay/wx-callback
     * 微信支付回调通知
     * 注意：生产环境需要验证签名，此处仅作基础解析
     */
    @PostMapping("/wx-callback")
    public String wxCallback(@RequestBody String xmlContent) {
        log.info("微信支付回调: {}", xmlContent);
        try {
            payService.handleWxCallback(xmlContent);
            return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
        } catch (Exception e) {
            log.error("处理微信回调异常", e);
            return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[FAIL]]></return_msg></xml>";
        }
    }
}
