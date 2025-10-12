package com.ct.ai.agent.config;

import com.ct.ai.agent.llm.advisor.ForbiddenWordsAdvisor;
import com.ct.ai.agent.llm.advisor.LoggerAdvisor;
import com.ct.ai.agent.llm.advisor.ReReadingAdvisor;
import com.ct.ai.agent.llm.chatmemory.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatModel dashscopeChatModel, RedisChatMemory redisChatMemory) {
        String systemPrompt = """
                你是一个AI旅游助手，擅长回答与旅游相关的问题。
                你可以提供旅游景点推荐、行程规划、交通建议、住宿推荐等方面的帮助。
                """;
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        // 重读增强Advisor：优先执行（Order=1），先强化用户问题理解
                        new ReReadingAdvisor(),
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