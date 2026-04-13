package com.timespace.module.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("pay_order")
public class PayOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单金额（元） */
    private BigDecimal amount;

    /** 墨晶数量 */
    private Integer inkStoneCount;

    /** 赠送墨晶数量（非持久化，仅计算用） */
    @TableField(exist = false)
    private Integer giftStoneCount;

    /** 套餐ID */
    private String packageId;

    /** 订单状态：0=待支付 1=已支付 2=已取消 3=已退款 */
    private Integer status;

    /** 微信支付交易单号 */
    private String wxTradeNo;

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    /** 订单创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_CANCELLED = 2;
    public static final int STATUS_REFUNDED = 3;
}
