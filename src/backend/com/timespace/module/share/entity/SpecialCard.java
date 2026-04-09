package com.timespace.module.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("special_card")
public class SpecialCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cardNo;              // 纪念卡编号

    private String name;                // 卡片名称

    private String description;         // 卡片描述

    private String imageUrl;            // 卡片图片URL

    private Long sourceStoryId;         // 来源故事ID

    private String sourceShareCode;     // 来源分享码

    private Integer rarity;             // 稀有度 1=凡 2=珍 3=奇 4=绝

    private LocalDateTime createdAt;
}
