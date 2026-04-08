package com.timespace.module.story.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("story_character")
public class StoryCharacter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private String name;             // 配角名称

    private Integer characterType;   // 1命运羁绊 2历史节点 3市井过客

    private Integer fateValue;       // 命运值 0-100

    private String fateDirection;    // 命运方向（变好/变坏）

    private String finalEpilogue;    // 最终后日谈判词

    private String relationToUser;   // 与用户扮演角色的关系

    private LocalDateTime createdAt;
}
