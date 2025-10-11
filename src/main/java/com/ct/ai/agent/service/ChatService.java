package com.ct.ai.agent.service;

import com.ct.ai.agent.vo.ReportVO;
import reactor.core.publisher.Flux;

/**
 * AI 聊天服务接口
 * 定义了与AI交互的核心业务方法，包括普通聊天、流式响应、结构化输出及工具调用等能力
 * 所有方法均基于聊天会话上下文（chatId）关联，确保多轮对话的连贯性
 */
public interface ChatService {

    /**
     * 普通同步聊天接口
     * 适用于简单对话场景，一次性返回AI完整响应结果
     *
     * @param message 用户输入的聊天内容（文本消息）
     * @param chatId  聊天会话唯一标识，用于关联上下文（如多轮对话历史）
     * @return AI生成的完整响应文本
     */
    String doChat(String message, String chatId);

    /**
     * 流式聊天接口（基础文本流）
     * 适用于需要实时展示AI响应的场景（如聊天机器人打字效果），逐段返回响应内容
     *
     * @param message 用户输入的聊天内容
     * @param chatId  聊天会话唯一标识
     * @return 响应式Flux流，包含AI响应的文本分片（按生成顺序推送）
     */
    Flux<String> doChatByStream(String message, String chatId);

    /**
     * 结构化输出接口
     * 适用于需要AI返回固定格式数据的场景（如报告、分析结果），返回预定义的VO对象
     *
     * @param message 用户输入的指令（通常包含对输出格式的要求）
     * @param chatId  聊天会话唯一标识
     * @return 结构化的报告对象（ReportVO），包含AI生成的结构化数据
     */
    ReportVO doChatWithStructuredOutput(String message, String chatId);

    /**
     * 带工具调用的流式聊天接口
     * 适用于AI需要调用外部工具（如搜索、计算、数据库查询等）的场景，流式返回工具调用结果
     *
     * @param message 用户输入的消息（可能触发AI自动调用工具）
     * @param chatId  聊天会话唯一标识
     * @return 响应式Flux流，包含工具调用过程及最终结果的文本分片
     */
    Flux<String> doChatByStreamWithTool(String message, String chatId);

    /**
     * 带工具调用及回调的流式聊天接口
     * 适用于需要多轮工具交互的场景（如工具调用结果需二次处理或多工具协同），支持回调逻辑处理中间结果
     *
     * @param message 用户输入的消息
     * @param chatId  聊天会话唯一标识
     * @return 响应式Flux流，包含工具调用、回调处理及最终结果的文本分片
     */
    Flux<String> doChatByStreamWithToolAndCallback(String message, String chatId);
}