package com.timespace.module.story.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@TableName("story_chapter")
public class StoryChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storyId;

    private Integer chapterNo;

    private String sceneText;        // 场景描写

    private List<Option> options;    // 选项列表

    private Integer selectedOption;  // 用户选择的选项ID

    private Map<Long, Integer> keywordResonance; // {cardId: 共鸣次数}

    private List<RippleEffect> ripples; // 涟漪效果

    private String aiFullText;       // AI生成的完整叙述

    private LocalDateTime createdAt;

    @Data
    public static class Option {
        private Integer id;
        private String text;          // 选项文字
        private String hint;          // 暗示（用户看不到的隐藏提示）
    }

    @Data
    public static class RippleEffect {
        private String target;        // 涟漪目标关键词
        private String status;        // 状态变化
    }
}
