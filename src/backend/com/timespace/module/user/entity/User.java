package com.timespace.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openId;           // 微信openId

    private String unionId;          // 微信unionId

    private String nickname;

    private String avatarUrl;

    private Integer inkStone;         // 墨晶数量

    private Integer dailyFreeDraws;   // 今日免费抽卡次数

    private LocalDateTime lastFreeResetTime; // 上次免费次数重置时间

    private Integer totalStories;    // 累计故事数

    private Integer completedStories;// 已完成故事数

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
