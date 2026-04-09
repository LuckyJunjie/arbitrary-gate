package com.timespace.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timespace.module.ai.entity.AiPromptTemplate;
import com.timespace.module.ai.mapper.AiPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AI Prompt 模板服务
 *
 * 负责从数据库加载 Agent 的 prompt 模板，支持运行时更新。
 * 提供 fallback 机制：数据库查询失败时返回硬编码的默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper promptTemplateMapper;

    // Agent 名称常量
    public static final String AGENT_STORYTELLER = "storyteller";
    public static final String AGENT_JUDGE = "judge";
    public static final String AGENT_BAIGUAN = "baiguan";
    public static final String AGENT_ZHANGYAN = "zhangyan";

    // Prompt Key 常量
    public static final String PROMPT_CHAPTER_SYSTEM = "chapter_system";
    public static final String PROMPT_MANUSCRIPT_SYSTEM = "manuscript_system";
    public static final String PROMPT_INSCRIPTION = "inscription";
    public static final String PROMPT_EVALUATION_SYSTEM = "evaluation_system";
    public static final String PROMPT_MANUSCRIPT_COMMENT = "manuscript_comment";
    public static final String PROMPT_AI腔_FILTER = "ai腔_filter";

    /**
     * 获取 Prompt 模板
     * 如果数据库中不存在，返回 Optional.empty()
     *
     * @param agentName Agent 名称
     * @param promptKey Prompt Key
     * @return Prompt 模板（如果存在）
     */
    public Optional<AiPromptTemplate> getPrompt(String agentName, String promptKey) {
        try {
            AiPromptTemplate template = promptTemplateMapper.findLatestByAgentAndKey(agentName, promptKey);
            return Optional.ofNullable(template);
        } catch (Exception e) {
            log.warn("查询 Prompt 模板失败: agentName={}, promptKey={}, error={}",
                    agentName, promptKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取 Prompt 文本
     * 如果数据库中不存在，返回 Optional.empty()
     *
     * @param agentName Agent 名称
     * @param promptKey Prompt Key
     * @return Prompt 文本（如果存在）
     */
    public Optional<String> getPromptText(String agentName, String promptKey) {
        return getPrompt(agentName, promptKey)
                .map(AiPromptTemplate::getPromptText);
    }

    /**
     * 获取 Prompt 文本，如果不存在则返回默认值
     *
     * @param agentName    Agent 名称
     * @param promptKey    Prompt Key
     * @param defaultValue 默认值
     * @return Prompt 文本
     */
    public String getPromptTextOrDefault(String agentName, String promptKey, String defaultValue) {
        return getPromptText(agentName, promptKey).orElse(defaultValue);
    }

    /**
     * 更新 Prompt 模板（创建新版本）
     *
     * @param agentName  Agent 名称
     * @param promptKey  Prompt Key
     * @param newText   新的 Prompt 内容
     * @return 更新是否成功
     */
    @CacheEvict(value = "aiPrompt", key = "#agentName + ':' + #promptKey")
    public boolean updatePrompt(String agentName, String promptKey, String newText) {
        try {
            // 查询当前最新版本
            AiPromptTemplate current = promptTemplateMapper.findLatestByAgentAndKey(agentName, promptKey);
            int newVersion = (current != null ? current.getVersion() : 0) + 1;

            AiPromptTemplate template = new AiPromptTemplate();
            template.setAgentName(agentName);
            template.setPromptKey(promptKey);
            template.setPromptText(newText);
            template.setVersion(newVersion);

            int result = promptTemplateMapper.insert(template);
            if (result > 0) {
                log.info("Prompt 模板更新成功: agent={}, key={}, version={}", agentName, promptKey, newVersion);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("更新 Prompt 模板失败: agentName={}, promptKey={}, error={}",
                    agentName, promptKey, e.getMessage());
            return false;
        }
    }

    /**
     * 检查数据库中是否存在指定的 Prompt 模板
     */
    public boolean promptExists(String agentName, String promptKey) {
        return getPrompt(agentName, promptKey).isPresent();
    }
}
