package com.timespace.module.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("story_share")
public class StoryShare {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shareCode;           // 分享码（8位）

    private Long storyId;              // 关联故事ID

    private Long creatorUserId;         // 分享创建者用户ID

    private Long missingCornerCardId;   // 缺角卡ID（关键词卡ID）

    private String cardName;            // 缺角卡名称

    private Integer cardCategory;       // 缺角卡分类

    private Integer status;             // 1=待合券 2=已合券 3=已过期

    private Long jointUserId;           // 合券者用户ID

    private LocalDateTime jointedAt;    // 合券时间

    private LocalDateTime expiresAt;    // 过期时间

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
