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
     * RAG流式对话接口
     * 接收用户消息和会话ID，返回流式结果（逐段推送回答，减少用户等待）
     *
     * @param message 用户输入的对话消息（如"介绍AI智能体的核心功能"）
     * @param chatId  会话ID（用于关联多轮对话上下文，避免上下文丢失）
     * @return 流式响应（ServerSentEvent）：每段回答内容封装为SSE事件
     */
    @GetMapping("/chat") // GET请求路径：/rag/chat
    public Flux<ServerSentEvent<String>> doChatWithRagQuery(@RequestParam(required = true) String message,
                                                            @RequestParam(required = true) String chatId) {

        return ragService.doChatWithRagQuery(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}