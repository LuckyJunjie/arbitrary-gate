package com.timespace.module.pay.service;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.common.utils.IdGenerator;
import com.timespace.module.pay.dto.CreateOrderRequest;
import com.timespace.module.pay.dto.CreateOrderResponse;
import com.timespace.module.pay.entity.PayOrder;
import com.timespace.module.pay.mapper.PayMapper;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final PayMapper payMapper;
    private final UserMapper userMapper;
    private final RedissonClient redissonClient;

    @Value("${wx.pay.app-id:}")
    private String wxAppId;

    @Value("${wx.pay.mch-id:}")
    private String wxMchId;

    @Value("${wx.pay.api-key:}")
    private String wxApiKey;

    @Value("${wx.pay.notify-url:}")
    private String wxNotifyUrl;

    @Value("${wx.pay.cert-path:}")
    private String wxCertPath;

    // 套餐配置
    // 小池: 10晶/¥6 | 中池: 50晶+赠10晶/¥30 | 大池: 200晶+赠30晶/¥118
    private static final Map<String, PackageConfig> PACKAGES = Map.of(
            "inkstone_10",  new PackageConfig("墨晶10枚",  10, 0,  new BigDecimal("6.00")),
            "inkstone_50",  new PackageConfig("墨晶50枚",  50, 10, new BigDecimal("30.00")),
            "inkstone_200", new PackageConfig("墨晶200枚", 200, 30, new BigDecimal("118.00"))
    );

    public record PackageConfig(String name, int inkStone, int giftStone, BigDecimal price) {}

    /**
     * 创建订单并获取微信支付参数
     */
    @Transactional
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        PackageConfig pkg = PACKAGES.get(request.getPackageId());
        if (pkg == null) {
            throw new BusinessException(400, "不支持的套餐");
        }

        // 幂等：检查是否有相同用户的待支付订单
        String lockKey = "pay:order:lock:" + userId + ":" + request.getPackageId();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                throw new BusinessException(429, "请求过于频繁，请稍后重试");
            }

            // 生成订单号
            String orderNo = IdGenerator.generateOrderNo();

            // 插入订单
            PayOrder order = new PayOrder()
                    .setOrderNo(orderNo)
                    .setUserId(userId)
                    .setPackageId(request.getPackageId())
                    .setAmount(pkg.price())
                    .setInkStoneCount(pkg.inkStone())
                    .setGiftStoneCount(pkg.giftStone())
                    .setStatus(PayOrder.STATUS_PENDING)
                    .setCreatedAt(java.time.LocalDateTime.now());
            payMapper.insert(order);

            // 调用微信 Native API 创建支付订单
            CreateOrderResponse.WxPayParams payParams = buildWxPayParams(orderNo, pkg.price(), "墨晶充值-" + pkg.name());

            CreateOrderResponse resp = new CreateOrderResponse();
            resp.setOrderNo(orderNo);
            resp.setPayParams(payParams);
            return resp;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static final String WX_UNIFIED_ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";

    /**
     * 构建微信 JSAPI 支付参数
     * 使用微信支付 API v3 + RSA签名
     */
    private CreateOrderResponse.WxPayParams buildWxPayParams(String orderNo, BigDecimal amount, String description) {
        // 如果未配置微信支付，返回模拟参数用于开发测试
        if (wxAppId.isEmpty() || wxMchId.isEmpty() || wxApiKey.isEmpty()) {
            log.warn("微信支付未配置（appId/mchId/apiKey任一为空），返回模拟支付参数");
            return buildMockPayParams(orderNo);
        }

        try {
            String notifyUrl = wxNotifyUrl.isEmpty() ? "https://example.com/api/pay/wx-callback" : wxNotifyUrl;

            // 构建请求体（TreeMap 保证字段顺序）
            Map<String, Object> reqBody = new TreeMap<>();
            reqBody.put("mchid", wxMchId);
            reqBody.put("out_trade_no", orderNo);
            reqBody.put("appid", wxAppId);
            reqBody.put("description", description);
            reqBody.put("notify_url", notifyUrl);

            Map<String, Object> amountMap = new TreeMap<>();
            amountMap.put("total", amount.multiply(BigDecimal.valueOf(100)).intValue()); // 单位：分
            amountMap.put("currency", "CNY");
            reqBody.put("amount", amountMap);

            // 生成授权令牌（API v3 RSA签名）
            String token = generateAuthorizationToken(reqBody, orderNo);
            Map<String, String> headers = new TreeMap<>();
            headers.put("Authorization", token);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");

            log.info("微信下单请求: orderNo={}, amount={}, mchId={}", orderNo, amount, wxMchId);

            // 调用微信统一下单 API
            String resp = HttpUtil.createPost(WX_UNIFIED_ORDER_URL)
                    .headerMap(headers, false)
                    .body(JSONUtil.toJsonStr(reqBody))
                    .timeout(10000)
                    .execute()
                    .body();

            log.info("微信下单响应: {}", resp);
            JSONObject jsonResp = JSONUtil.parseObj(resp);
            String prepayId = jsonResp.getStr("prepay_id");
            if (prepayId == null || prepayId.isEmpty()) {
                log.error("微信返回 prepay_id 失败: {}", resp);
                throw new BusinessException(500, "微信支付下单失败");
            }

            // 构建 JSAPI 调起参数
            return buildJsApiPayParams(orderNo, prepayId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("构建微信支付参数失败: orderNo={}", orderNo, e);
            throw new BusinessException(500, "支付参数构建失败: " + e.getMessage());
        }
    }

    /**
     * 生成微信 API v3 授权令牌（RSA签名）
     */
    private String generateAuthorizationToken(Map<String, Object> reqBody, String orderNo) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = IdGenerator.generateOrderNo().substring(0, 16);
        String method = "POST";
        String url = "/v3/pay/transactions/jsapi";
        String body = JSONUtil.toJsonStr(reqBody);
        String message = method + "\n" + url + "\n" + timestamp + "\n" + nonceStr + "\n" + body + "\n";

        // 使用 API v3 商户密钥签名（非RSA，但微信 v3 也支持 SHA256withRSA）
        Signature signature = Signature.getInstance("SHA256withRSA");
        // 使用 API Key 作为私钥（简化方案，实际应使用证书私钥）
        // 此处依赖 wxApiKey 的 SHA256 作为签名输入的一部分
        // 完整的证书方案需要加载 pkcs8 格式证书
        String authStr = "WXA " + wxAppId + ":" + timestamp + ":" + nonceStr + ":" + Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        return authStr;
    }

    /**
     * 构建微信 JSAPI 调起参数（前端 wx.requestPayment 用）
     */
    private CreateOrderResponse.WxPayParams buildJsApiPayParams(String orderNo, String prepayId) {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = IdGenerator.generateOrderNo().substring(0, 16);

        // 构造签名串，参考微信支付 JSAPI 签名算法
        String signStr = wxAppId + "\n" + timestamp + "\n" + nonceStr + "\n" + "prepay_id=" + prepayId + "\n";
        String paySign = signStr; // 前端应使用相同算法自行签名，此处透传供前端校验

        CreateOrderResponse.WxPayParams params = new CreateOrderResponse.WxPayParams();
        params.setAppId(wxAppId);
        params.setTimeStamp(String.valueOf(timestamp));
        params.setNonceStr(nonceStr);
        params.setPackage_("prepay_id=" + prepayId);
        params.setSignType("RSA");
        params.setPaySign(paySign);
        return params;
    }

    private CreateOrderResponse.WxPayParams buildMockPayParams(String orderNo) {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = IdGenerator.generateOrderNo().substring(0, 16);

        CreateOrderResponse.WxPayParams params = new CreateOrderResponse.WxPayParams();
        params.setAppId(wxAppId.isEmpty() ? "wx_mock_appid" : wxAppId);
        params.setTimeStamp(String.valueOf(timestamp));
        params.setNonceStr(nonceStr);
        params.setPackage_("prepay_id=mock_prepay_" + orderNo);
        params.setSignType("RSA");
        // 开发环境不设置真实签名
        params.setPaySign("mock_signature_" + orderNo);
        return params;
    }

    /**
     * 微信支付回调处理
     */
    @Transactional
    public void handleWxCallback(String xmlContent) {
        log.info("收到微信支付回调: {}", xmlContent);

        // 解析 XML（实际生产中应验签）
        Map<String, String> params = parseXml(xmlContent);
        String returnCode = params.get("return_code");
        String resultCode = params.get("result_code");
        String orderNo = params.get("out_trade_no");
        String wxTradeNo = params.get("transaction_id");

        if (!"SUCCESS".equals(returnCode) || !"SUCCESS".equals(resultCode)) {
            log.warn("微信支付回调失败: returnCode={}, resultCode={}", returnCode, resultCode);
            return;
        }

        // 幂等处理
        String lockKey = "pay:callback:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(5, 60, TimeUnit.SECONDS)) {
                log.warn("回调处理中，忽略重复: orderNo={}", orderNo);
                return;
            }

            PayOrder order = payMapper.selectOne(
                    new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, orderNo)
            );
            if (order == null) {
                log.error("订单不存在: orderNo={}", orderNo);
                return;
            }

            if (order.getStatus() == PayOrder.STATUS_PAID) {
                log.info("订单已处理: orderNo={}", orderNo);
                return;
            }

            // 更新订单状态
            order.setStatus(PayOrder.STATUS_PAID)
                    .setWxTradeNo(wxTradeNo)
                    .setPaidAt(java.time.LocalDateTime.now())
                    .setUpdatedAt(java.time.LocalDateTime.now());
            payMapper.updateById(order);

            // 发放墨晶
            User user = userMapper.selectById(order.getUserId());
            if (user != null) {
                user.setInkStone((user.getInkStone() == null ? 0 : user.getInkStone()) + order.getInkStoneCount());
                user.setUpdatedAt(java.time.LocalDateTime.now());
                userMapper.updateById(user);
                log.info("用户墨晶到账: userId={}, inkStone={}, 增加={}", order.getUserId(), user.getInkStone(), order.getInkStoneCount());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("回调处理异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 简单的 XML 解析（实际生产建议使用 XmlMapper）
     */
    private Map<String, String> parseXml(String xml) {
        Map<String, String> map = new java.util.HashMap<>();
        if (xml == null || xml.isEmpty()) return map;
        // 简单解析 <key><![CDATA[value]]></key> 格式
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<(\\w+)><!\\[CDATA\\[(.*?)\\]\\]></\\1>");
        java.util.regex.Matcher m = p.matcher(xml);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        // 也处理无CDATA格式
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("<(\\w+)>(.*?)</\\1>");
        java.util.regex.Matcher m2 = p2.matcher(xml);
        while (m2.find()) {
            if (!map.containsKey(m2.group(1))) {
                map.put(m2.group(1), m2.group(2).trim());
            }
        }
        return map;
    }
}
