package com.timespace.module.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卡池扩展包实体
 * 用于管理卡池数据的分包扩展机制
 * 表结构：card_expansion
 */
@Data
@TableName("card_expansion")
public class CardExpansion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 扩展包代码，如 'core', 'legend', 'wuxia' */
    private String expansionCode;

    /** 扩展包名称，如 '核心卡池', '传说扩展', '武侠风云' */
    private String expansionName;

    /** 扩展包描述 */
    private String description;

    /** 卡牌数量（冗余字段，便于前端展示） */
    private Integer cardCount;

    /** 是否启用：1=启用，0=禁用 */
    private Integer enabled;

    /** 排序顺序（数字越小越靠前） */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
