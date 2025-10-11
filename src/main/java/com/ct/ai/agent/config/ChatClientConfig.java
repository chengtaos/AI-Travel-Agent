package com.ct.ai.agent.config;

import com.ct.ai.agent.llm.advisor.ForbiddenWordsAdvisor;
import com.ct.ai.agent.llm.advisor.LoggerAdvisor;
import com.ct.ai.agent.llm.chatmemory.RedisChatMemory;
import com.ct.ai.agent.llm.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatModel dashscopeChatModel, RedisChatMemory redisChatMemory) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem(Prompt.SYSTEM_PROMPT)
                .defaultAdvisors(
                        // 聊天记忆 - 使用 Redis 存储聊天记忆
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build(),
                        // 聊天记忆 - 使用数据库存储聊天记忆
                        // MessageChatMemoryAdvisor.builder(databaseChatMemory).build()
                        // 自定义日志 - 打印 info 级别日志，只输出单次用户提示词和AI回复的文本
                        new LoggerAdvisor(),
                        // 自定义禁止词 - 过滤不当内容
                        new ForbiddenWordsAdvisor()
                ).build();
    }

}