package com.ct.ai.agent.controller;

import com.ct.ai.agent.service.RagService;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 提供基于RAG技术的对话接口，支持流式返回结果，提升大模型回答的准确性和时效性
 */
@RestController
@RequestMapping("/rag")
public class RagController {
    @Resource
    private RagService ragService;

    /**
     * RAG流式对话接口（支持指定检索策略）
     * 接收用户消息、会话ID、检索策略，返回流式SSE结果
     *
     * @param message 用户输入的对话消息（如"介绍AI智能体的核心功能"）
     * @param chatId  会话ID（用于关联多轮对话上下文，避免上下文丢失）
     * @param strategy 可选：检索策略名称（local-pgvector/aliyun-dashscope，默认用配置的rag.default-strategy）
     * @return 流式响应（ServerSentEvent）：每段回答内容封装为SSE事件
     */
    @GetMapping("/chat") // GET请求路径：/rag/chat
    public Flux<ServerSentEvent<String>> doChatWithRagQuery(
            @RequestParam(required = true) String message,
            @RequestParam(required = true) String chatId,
            @RequestParam(required = false) String strategy) {

        // 透传参数给Service：若strategy为null，Service会使用默认策略
        return ragService.doChatWithRagQuery(message, chatId, strategy)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk) // 流式返回的回答片段
                        .id(String.valueOf(System.currentTimeMillis())) // 给每个事件加唯一ID（便于前端追踪）
                        .event("answerChunk") // 可选：标记事件类型（前端可按类型处理）
                        .build());
    }
}