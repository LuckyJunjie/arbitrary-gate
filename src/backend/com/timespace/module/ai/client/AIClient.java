package com.timespace.module.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * AI 客户端 - 统一对接通义千问（阿里云百炼）
 *
 * 支持：
 * 1. 同步调用（普通对话）
 * 2. 流式调用（故事章节生成，支持 WebSocket 推送）
 */
@Slf4j
@Component
public class AIClient {

    private final ChatClient chatClient;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-turbo}")
    private String defaultModel;

    public AIClient(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 同步调用（阻塞）
     * 用于判官评估、选项生成等短文本任务
     */
    public String callSync(String systemPrompt, String userMessage) {
        log.debug("AI同步调用: systemPrompt长度={}, userMessage={}",
                systemPrompt.length(), userMessage.length());
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            log.debug("AI同步响应: 长度={}", response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            log.error("AI同步调用失败", e);
            throw new com.timespace.common.exception.BusinessException(500, "AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 流式调用（返回 Flux，适合长文本生成）
     * 用于说书人生成故事章节
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param onChunk      每个chunk的回调（WebSocket推送用）
     * @return 完整响应文本
     */
    public String callStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        log.info("AI流式调用开始: model={}", defaultModel);
        StringBuilder fullResponse = new StringBuilder();

        try {
            Flux<ChatResponse> flux = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .stream();

            // 使用 MessageAggregator 将流式响应聚合
            MessageAggregator aggregator = new MessageAggregator();
            aggregator.aggregate(flux, new Consumer<ChatResponse>() {
                @Override
                public void accept(ChatResponse chatResponse) {
                    if (chatResponse != null && chatResponse.getResult() != null) {
                        String chunk = chatResponse.getResult().getOutput().getText();
                        if (chunk != null && !chunk.isEmpty()) {
                            fullResponse.append(chunk);
                            onChunk.accept(chunk); // 实时推送
                        }
                    }
                }
            });

            String result = fullResponse.toString();
            log.info("AI流式调用完成: 总长度={}", result.length());
            return result;

        } catch (Exception e) {
            log.error("AI流式调用失败", e);
            throw new com.timespace.common.exception.BusinessException(500, "AI生成失败: " + e.getMessage());
        }
    }

    /**
     * 流式调用（仅返回 Flux，由调用方处理）
     * 适用于需要更精细控制的场景
     */
    public Flux<ChatResponse> stream(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream();
    }
}
