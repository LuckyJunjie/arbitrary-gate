package com.timespace.module.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timespace.common.exception.BusinessException;
import com.timespace.module.pay.dto.CreateOrderRequest;
import com.timespace.module.pay.dto.CreateOrderResponse;
import com.timespace.module.pay.entity.PayOrder;
import com.timespace.module.pay.mapper.PayMapper;
import com.timespace.module.user.entity.User;
import com.timespace.module.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PayService 单元测试
 *
 * 测试覆盖：
 * - U-05 订单创建（createOrder）
 * - U-05 微信回调处理（handleWxCallback）
 * - 套餐价格验证
 * - 幂等性（同一 orderNo 不重复创建）
 * - 订单状态流转（PENDING → PAID）
 * - 墨晶发放逻辑
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PayServiceTest {

    @Mock
    private PayMapper payMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private PayService payService;

    @BeforeEach
    void setUp() throws InterruptedException {
        payService = new PayService(payMapper, userMapper, redissonClient);
        // 默认开启 @Value 注入（通过反射手动注入）
        setField(payService, "wxAppId", "wx_mock_appid");
        setField(payService, "wxMchId", "wx_mock_mchid");
        setField(payService, "wxApiKey", "mock_apikey");
        setField(payService, "wxNotifyUrl", "https://example.com/api/pay/wx-callback");
        setField(payService, "wxCertPath", "");

        // 默认 RLock mock：tryLock 立即成功
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    // ========== 套餐价格验证测试 ==========

    @Test
    @DisplayName("小池套餐: inkstone_10 = ¥6.00，含 0 赠晶")
    void createOrder_smallPool_correctPrice() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_10");
        request.setInkStoneCount(10);
        request.setAmount(new BigDecimal("6.00"));

        when(payMapper.insert(any(PayOrder.class))).thenReturn(1);

        // WHEN
        CreateOrderResponse resp = payService.createOrder(userId, request);

        // THEN
        assertNotNull(resp.getOrderNo());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payMapper).insert(captor.capture());

        PayOrder savedOrder = captor.getValue();
        assertEquals(new BigDecimal("6.00"), savedOrder.getAmount());
        assertEquals(10, savedOrder.getInkStoneCount());
        assertEquals(0, savedOrder.getGiftStoneCount());
        assertEquals(PayOrder.STATUS_PENDING, savedOrder.getStatus());
        assertEquals("inkstone_10", savedOrder.getPackageId());
    }

    @Test
    @DisplayName("中池套餐: inkstone_50 = ¥30.00，含 10 赠晶")
    void createOrder_mediumPool_correctPriceAndGift() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_50");
        request.setInkStoneCount(50);
        request.setAmount(new BigDecimal("30.00"));

        when(payMapper.insert(any(PayOrder.class))).thenReturn(1);

        // WHEN
        CreateOrderResponse resp = payService.createOrder(userId, request);

        // THEN
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payMapper).insert(captor.capture());

        PayOrder savedOrder = captor.getValue();
        assertEquals(new BigDecimal("30.00"), savedOrder.getAmount());
        assertEquals(50, savedOrder.getInkStoneCount());
        assertEquals(10, savedOrder.getGiftStoneCount());
    }

    @Test
    @DisplayName("大池套餐: inkstone_200 = ¥118.00，含 30 赠晶")
    void createOrder_largePool_correctPriceAndGift() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_200");
        request.setInkStoneCount(200);
        request.setAmount(new BigDecimal("118.00"));

        when(payMapper.insert(any(PayOrder.class))).thenReturn(1);

        // WHEN
        CreateOrderResponse resp = payService.createOrder(userId, request);

        // THEN
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payMapper).insert(captor.capture());

        PayOrder savedOrder = captor.getValue();
        assertEquals(new BigDecimal("118.00"), savedOrder.getAmount());
        assertEquals(200, savedOrder.getInkStoneCount());
        assertEquals(30, savedOrder.getGiftStoneCount());
    }

    @Test
    @DisplayName("创建订单: 非法套餐ID抛出 BusinessException(400)")
    void createOrder_invalidPackageId_throws400() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("invalid_package");
        request.setInkStoneCount(10);
        request.setAmount(new BigDecimal("6.00"));

        // WHEN & THEN
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payService.createOrder(userId, request));
        assertEquals(400, ex.getCode());
        assertEquals("不支持的套餐", ex.getMessage());
    }

    @Test
    @DisplayName("创建订单: 获取分布式锁失败抛出 BusinessException(429)")
    void createOrder_lockFailed_throws429() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_10");
        request.setInkStoneCount(10);
        request.setAmount(new BigDecimal("6.00"));

        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("lock timeout"));

        // WHEN & THEN
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payService.createOrder(userId, request));
        assertEquals(429, ex.getCode());
    }

    @Test
    @DisplayName("创建订单: 返回的 orderNo 格式为非空字符串（UUID-based）")
    void createOrder_returnsNonEmptyOrderNo() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_10");
        request.setInkStoneCount(10);
        request.setAmount(new BigDecimal("6.00"));

        when(payMapper.insert(any(PayOrder.class))).thenReturn(1);

        // WHEN
        CreateOrderResponse resp = payService.createOrder(userId, request);

        // THEN
        assertNotNull(resp.getOrderNo());
        assertFalse(resp.getOrderNo().isEmpty());
        assertTrue(resp.getOrderNo().length() > 10);
    }

    @Test
    @DisplayName("创建订单: 订单状态初始化为 PENDING(0)")
    void createOrder_initialStatusIsPending() throws InterruptedException {
        // GIVEN
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageId("inkstone_10");
        request.setInkStoneCount(10);
        request.setAmount(new BigDecimal("6.00"));

        when(payMapper.insert(any(PayOrder.class))).thenReturn(1);

        // WHEN
        payService.createOrder(userId, request);

        // THEN
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payMapper).insert(captor.capture());
        assertEquals(PayOrder.STATUS_PENDING, captor.getValue().getStatus());
    }

    // ========== 微信回调测试 ==========

    @Test
    @DisplayName("微信回调: 支付成功则更新订单状态为 PAID + 发放墨晶")
    void handleWxCallback_paid_success() throws InterruptedException {
        // GIVEN: 订单存在且为待支付状态
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[SUCCESS]]></return_code>
                    <result_code><![CDATA[SUCCESS]]></result_code>
                    <out_trade_no><![CDATA[ORDER_NO_123]]></out_trade_no>
                    <transaction_id><![CDATA[WX_TRANSACTION_456]]></transaction_id>
                </xml>
                """;

        PayOrder pendingOrder = new PayOrder()
                .setId(1L)
                .setOrderNo("ORDER_NO_123")
                .setUserId(10L)
                .setInkStoneCount(10)
                .setAmount(new BigDecimal("6.00"))
                .setStatus(PayOrder.STATUS_PENDING);

        User user = new User()
                .setId(10L)
                .setInkStone(50);

        when(payMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingOrder);
        when(userMapper.selectById(10L)).thenReturn(user);
        when(payMapper.updateById(any(PayOrder.class))).thenReturn(1);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // WHEN
        payService.handleWxCallback(xmlContent);

        // THEN: 验证订单状态更新
        ArgumentCaptor<PayOrder> orderCaptor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payMapper).updateById(orderCaptor.capture());
        PayOrder updatedOrder = orderCaptor.getValue();
        assertEquals(PayOrder.STATUS_PAID, updatedOrder.getStatus());
        assertEquals("WX_TRANSACTION_456", updatedOrder.getWxTradeNo());
        assertNotNull(updatedOrder.getPaidAt());

        // 验证墨晶发放
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertEquals(60, userCaptor.getValue().getInkStone()); // 50 + 10
    }

    @Test
    @DisplayName("微信回调: return_code 非 SUCCESS 时不处理")
    void handleWxCallback_returnCodeFail_ignored() {
        // GIVEN: return_code = FAIL
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[FAIL]]></return_code>
                    <return_msg><![CDATA[签名失败]]></return_msg>
                </xml>
                """;

        // WHEN
        payService.handleWxCallback(xmlContent);

        // THEN: payMapper 不被调用
        verify(payMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(payMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("微信回调: result_code 非 SUCCESS 时不处理")
    void handleWxCallback_resultCodeFail_ignored() {
        // GIVEN: return_code=SUCCESS 但 result_code=FAIL
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[SUCCESS]]></return_code>
                    <result_code><![CDATA[FAIL]]></result_code>
                    <out_trade_no><![CDATA[ORDER_NO_123]]></out_trade_no>
                </xml>
                """;

        // WHEN
        payService.handleWxCallback(xmlContent);

        // THEN
        verify(payMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("微信回调: 订单不存在时直接返回（不抛异常）")
    void handleWxCallback_orderNotFound_noException() {
        // GIVEN
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[SUCCESS]]></return_code>
                    <result_code><![CDATA[SUCCESS]]></result_code>
                    <out_trade_no><![CDATA[NON_EXISTENT_ORDER]]></out_trade_no>
                    <transaction_id><![CDATA[WX_TXN_999]]></transaction_id>
                </xml>
                """;

        when(payMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // WHEN & THEN: 不抛异常
        assertDoesNotThrow(() -> payService.handleWxCallback(xmlContent));
        verify(payMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("微信回调: 订单已为PAID状态时幂等跳过（不重复发放墨晶）")
    void handleWxCallback_alreadyPaid_idempotent() throws InterruptedException {
        // GIVEN: 订单已支付
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[SUCCESS]]></return_code>
                    <result_code><![CDATA[SUCCESS]]></result_code>
                    <out_trade_no><![CDATA[ORDER_NO_123]]></out_trade_no>
                    <transaction_id><![CDATA[WX_TXN_REPEAT]]></transaction_id>
                </xml>
                """;

        PayOrder paidOrder = new PayOrder()
                .setId(1L)
                .setOrderNo("ORDER_NO_123")
                .setUserId(10L)
                .setInkStoneCount(10)
                .setStatus(PayOrder.STATUS_PAID) // 已是PAID状态
                .setWxTradeNo("WX_FIRST_TXN");

        when(payMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paidOrder);

        // WHEN
        payService.handleWxCallback(xmlContent);

        // THEN: updateById 不被调用（幂等跳过）
        verify(payMapper, never()).updateById(any(PayOrder.class));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("微信回调: 用户不存在时不抛异常（仅记录日志）")
    void handleWxCallback_userNotFound_noException() throws InterruptedException {
        // GIVEN
        String xmlContent = """
                <xml>
                    <return_code><![CDATA[SUCCESS]]></return_code>
                    <result_code><![CDATA[SUCCESS]]></result_code>
                    <out_trade_no><![CDATA[ORDER_NO_123]]></out_trade_no>
                    <transaction_id><![CDATA[WX_TXN_456]]></transaction_id>
                </xml>
                """;

        PayOrder pendingOrder = new PayOrder()
                .setId(1L)
                .setOrderNo("ORDER_NO_123")
                .setUserId(99L)
                .setInkStoneCount(10)
                .setStatus(PayOrder.STATUS_PENDING);

        when(payMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingOrder);
        when(userMapper.selectById(99L)).thenReturn(null); // 用户不存在

        // WHEN & THEN: 不抛异常
        assertDoesNotThrow(() -> payService.handleWxCallback(xmlContent));
        // 订单状态仍更新（即使墨晶发放失败）
        verify(payMapper).updateById(any(PayOrder.class));
    }

    // ========== 状态常量测试 ==========

    @Test
    @DisplayName("PayOrder 状态常量: PENDING=0, PAID=1, CANCELLED=2, REFUNDED=3")
    void payOrderStatusConstants_correctValues() {
        assertEquals(0, PayOrder.STATUS_PENDING);
        assertEquals(1, PayOrder.STATUS_PAID);
        assertEquals(2, PayOrder.STATUS_CANCELLED);
        assertEquals(3, PayOrder.STATUS_REFUNDED);
    }

    // ========== Helper ==========

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
