package com.timespace.module.story.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * S-14 故事偶遇表
 * 章节间随机触发的配角偶遇事件
 */
@Data
@Accessors(chain = true)
@TableName("story_encounter")
public class StoryEncounter {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属故事ID */
    private Long storyId;

    /** 触发章节号（用户选择后进入下一章） */
    private Integer chapterNo;

    /** 偶遇场景描述（50-80字） */
    private String encounterText;

    /** 选项A文字 */
    private String optionA;

    /** 选项B文字 */
    private String optionB;

    /** 用户选择结果：A / B */
    private String choiceResult;

    /** 命运值变化：搭话+10，装没看见-5 */
    private Integer fateChange;
}
