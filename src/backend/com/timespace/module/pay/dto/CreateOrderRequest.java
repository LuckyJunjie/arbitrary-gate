package com.timespace.module.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /** 套餐ID：inkstone_10 / inkstone_50 / inkstone_200 */
    @NotBlank(message = "套餐ID不能为空")
    private String packageId;

    /** 墨晶数量 */
    @NotNull(message = "墨晶数量不能为空")
    @Positive(message = "墨晶数量必须为正数")
    private Integer inkStoneCount;

    /** 金额（元） */
    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须为正数")
    private BigDecimal amount;
}
