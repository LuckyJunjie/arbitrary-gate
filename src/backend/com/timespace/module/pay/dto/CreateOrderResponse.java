package com.timespace.module.pay.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建订单响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderResponse {

    /** 订单号 */
    private String orderNo;

    /** 预支付会话ID（JSAPI支付用） */
    private String prepayId;

    /** 支付参数（wx.chooseWXPay 用） */
    private WxPayParams payParams;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WxPayParams {
        private String appId;
        private String timeStamp;
        private String nonceStr;
        private String package_;
        private String signType;
        private String paySign;
    }
}
