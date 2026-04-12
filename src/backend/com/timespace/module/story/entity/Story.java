package com.timespace.module.story.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName("story")
public class Story {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String storyNo;          // 分享编号

    private Long userId;

    private Long eventCardId;        // 事件卡ID

    private List<Long> keywordCardIds; // 关键词卡ID列表

    private Integer identityType;     // 1高位 2低位 3旁观者

    private String entryAnswers;      // 入局三问答案 JSON

    private String title;            // 故事标题

    private String candidateTitles;  // 备选标题 JSON 数组

    private Integer style;           // 1白描 2江湖 3笔记 4话本

    private Integer totalWords;      // 总字数

    private Integer historyDeviation; // 历史偏离度 0-100

    private Integer status;          // 1进行中 2已完成 3已归档

    private String endingType;       // 结局类型：功成名就/归隐山林/悲剧收场/爱情圆满/友情长存/宿命难逃/意外转折/平淡是真

    private Integer currentChapter;  // 当前章节

    private String contextJson;      // 故事上下文 JSON（存储配角命运等）

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    @TableLogic
    private Integer deleted;
}
