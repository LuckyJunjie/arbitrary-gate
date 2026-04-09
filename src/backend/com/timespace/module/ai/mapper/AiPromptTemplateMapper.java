package com.timespace.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timespace.module.ai.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI Prompt 模板 Mapper
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {

    /**
     * 根据 Agent 名称和 Prompt Key 获取模板
     */
    @Select("SELECT * FROM ai_prompt_template WHERE agent_name = #{agentName} AND prompt_key = #{promptKey} LIMIT 1")
    AiPromptTemplate findByAgentAndKey(@Param("agentName") String agentName, @Param("promptKey") String promptKey);

    /**
     * 获取最新的 Prompt 模板（按版本号降序）
     */
    @Select("SELECT * FROM ai_prompt_template WHERE agent_name = #{agentName} AND prompt_key = #{promptKey} ORDER BY version DESC LIMIT 1")
    AiPromptTemplate findLatestByAgentAndKey(@Param("agentName") String agentName, @Param("promptKey") String promptKey);
}
