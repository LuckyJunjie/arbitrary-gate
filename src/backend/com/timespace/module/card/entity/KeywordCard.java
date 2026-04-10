package com.timespace.module.card.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("keyword_card")
public class KeywordCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cardNo;          // 卡片编号 K + 4位类别 + 4位序号

    private String name;            // 关键词（2-3字）

    private Integer category;       // 1器物 2职人 3风物 4情绪 5称谓

    private Integer rarity;         // 1凡 2珍 3奇 4绝

    private String description;     // 描述

    private String imageUrl;        // 卡面图URL

    /** P-04 意象标签：如"水"表示水相关意象，用于三水彩蛋检测。渡口/船/江/河/雨/水/舟/帆/潮/浪 */
    private String tag;

    private Integer weight;         // 抽中权重

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
