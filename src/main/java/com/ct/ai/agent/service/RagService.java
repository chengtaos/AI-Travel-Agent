package com.ct.ai.agent.service;

import reactor.core.publisher.Flux;

public interface RagService {

    /**
     * 重载1：使用默认检索策略
     */
    Flux<String> doChatWithRagQuery(String message, String chatId);

    /**
     * 支持指定检索策略（按需切换本地/阿里云等）
     * @param message 用户问题
     * @param chatId 会话ID
     * @param strategy 检索策略名称（如"local-pgvector"、"aliyun-dashscope"）
     * @return 流式回答
     */
    Flux<String> doChatWithRagQuery(String message, String chatId, String strategy);
}