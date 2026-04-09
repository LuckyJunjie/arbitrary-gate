package com.timespace.module.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户持有的事件卡牌记录
 * 表结构：user_card（card_type='event'）
 */
@Data
@TableName("user_card")
public class UserEventCard implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 卡牌类型：keyword / event */
    private String cardType;

    /** 事件卡ID */
    private Integer cardId;

    /** 卡牌编号 */
    private String cardNo;

    /** 持有数量 */
    private Integer count;

    /** 获得时间 */
    private LocalDateTime acquiredAt;
}
