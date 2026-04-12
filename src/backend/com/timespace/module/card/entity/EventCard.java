package com.timespace.module.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

/**
 * 历史事件卡牌实体
 * 表结构：event_card
 */
@Data
@TableName("event_card")
public class EventCard implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 卡牌编号，如 EV001 */
    private String cardNo;

    /** 事件标题，如 "巨鹿·破釜沉舟" */
    private String title;

    /** 所属朝代，如 "秦"、"三国" */
    private String dynasty;

    /** 发生地点 */
    private String location;

    /** 事件描述 */
    private String description;

    /** 抽卡权重 */
    private Integer weight;

    /** 时代标签，如 "秦末汉初" */
    private String era;

    /** 所属扩展包：core=核心卡池，legend=传说扩展，wuxia=武侠扩展 */
    private String expansion;
}
