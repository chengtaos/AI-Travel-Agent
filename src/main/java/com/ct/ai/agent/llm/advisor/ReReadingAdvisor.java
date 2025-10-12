package com.ct.ai.agent.llm.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * 重读增强Advisor（Re-Reading Advisor）
 * 基于论文《Re2: Towards Re-reading Enhanced Language Models for Better Reasoning》
 * 核心功能：通过重复用户问题强化模型理解，适配UserMessage无getContent()方法的Spring AI版本
 */
@Slf4j
public class ReReadingAdvisor implements BaseAdvisor {

    // 重读提示模板：遵循论文RE2技术的重复逻辑
    private static final String RE2_PROMPT_TEMPLATE = "%s\nRead the question again: %s";

    @Override
    public int getOrder() {
        return 1; // 优先执行，确保提示增强生效
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1. 入参校验：避免空指针异常
        Assert.notNull(chatClientRequest, "ChatClientRequest 不能为空");
        Assert.notNull(chatClientRequest.prompt(), "原始请求的Prompt 不能为空");

        // 2. 提取用户原始问题
        UserMessage originalUserMsg = chatClientRequest.prompt().getUserMessage();
        Assert.notNull(originalUserMsg, "Prompt中未包含用户消息（UserMessage）");

        String originalQuery = originalUserMsg.getText();
        Assert.hasText(originalQuery, "用户消息内容不能为空");
        log.debug("[ReReadingAdvisor] 原始用户问题：{}", originalQuery);

        // 3. 保存原始问题到上下文，便于追踪
        Map<String, Object> enhancedContext = new HashMap<>(chatClientRequest.context());
        enhancedContext.put("re2_original_query", originalQuery);

        // 4. 构建RE2增强提示
        String enhancedPromptContent = String.format(RE2_PROMPT_TEMPLATE, originalQuery, originalQuery);
        log.debug("[ReReadingAdvisor] 增强后提示：{}", enhancedPromptContent);

        // 5. 生成增强后的Prompt
        Prompt enhancedPrompt = Prompt.builder()
                .content(enhancedPromptContent)
                .build();

        // 6. 返回增强后的请求
        return ChatClientRequest.builder()
                .context(enhancedContext)
                .prompt(enhancedPrompt)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse; // 透传响应，不做修改
    }
}