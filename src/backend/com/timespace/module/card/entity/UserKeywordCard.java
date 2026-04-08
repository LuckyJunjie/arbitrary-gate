package com.timespace.module.card.entity;

import com.timespace.module.card.entity.KeywordCard;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_keyword_card")
public class UserKeywordCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long cardId;            // keyword_card.id

    private Integer inkFragrance;   // 墨香值(0-7)

    private Integer resonanceCount; // 累计共鸣次数

    private LocalDateTime acquiredAt;

    @TableLogic
    private Integer deleted;
}
