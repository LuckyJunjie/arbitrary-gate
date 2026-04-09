package com.timespace.module.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_special_card")
public class UserSpecialCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long specialCardId;

    private String sourceShareCode;

    private LocalDateTime acquiredAt;
}
