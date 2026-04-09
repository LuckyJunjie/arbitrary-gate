package com.timespace.module.share.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("story_reader")
public class StoryReader {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Long userId;

    private String sourceShareCode;

    private LocalDateTime readAt;
}
