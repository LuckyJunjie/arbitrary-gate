package com.timespace.module.story.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@TableName("story_manuscript")
public class StoryManuscript {

    @TableId(type = IdType.AUTO)
    private Long storyId;

    private String fullText;         // 完整小说正文

    private List<Annotation> annotations; // 朱批位置及内容

    private List<ChoiceMark> choiceMarks; // 用户选择标记

    private String epilogue;         // 后日谈

    private String baiguanComment;  // 稗官评语

    private Integer wordCount;       // 字数统计

    private LocalDateTime createdAt;

    @Data
    public static class Annotation {
        private Integer chapterNo;   // 章节号
        private String position;     // 位置描述
        private String text;         // 朱批内容
        private String color;        // 朱砂颜色
    }

    @Data
    public static class ChoiceMark {
        private Integer chapterNo;
        private Integer optionId;
        private String text;
    }
}
