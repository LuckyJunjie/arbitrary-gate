package com.timespace.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI Prompt 模板实体
 *
 * 用于存储 Agent 的 system prompt，支持运行时更新。
 * 每个 Agent 有多个 prompt_key 对应不同的使用场景。
 */
@Data
@Accessors(chain = true)
@TableName("ai_prompt_template")
public class AiPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Agent 名称: storyteller/judge/baiguan/zhangyan */
    private String agentName;

    /** Prompt 标识符 */
    private String promptKey;

    /** Prompt 内容 */
    private String promptText;

    /** 版本号 */
    private Integer version;

    /** Prompt 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
