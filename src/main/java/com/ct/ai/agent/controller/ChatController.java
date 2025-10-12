package com.ct.ai.agent.controller;

import com.ct.ai.agent.service.ChatService;
import com.ct.ai.agent.vo.BaseResponse;
import com.ct.ai.agent.vo.ReportVO;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * AI 聊天控制器
 * 提供各类 AI 对话交互接口，支持普通文本响应、流式响应、工具调用等功能
 */
@RestController
@RequestMapping("/ai")
public class ChatController {
    @Resource
    private ChatService chatService;

    /**
     * 普通文本聊天接口
     * 同步返回完整的 AI 响应结果
     *
     * @param message 用户输入的聊天消息
     * @param chatId  聊天会话唯一标识（用于关联上下文）
     * @return 包含 AI 响应文本的统一响应对象
     */
    @GetMapping("/chat")
    public BaseResponse<String> doChat(@RequestParam String message, @RequestParam String chatId) {
        try {
            String result = chatService.doChat(message, chatId);
            return BaseResponse.success(result);
        } catch (Exception e) {
            return BaseResponse.businessError("聊天服务异常：" + e.getMessage());
        }
    }

    /**
     * 流式聊天接口（基础文本流）
     * 以 TEXT_EVENT_STREAM 格式返回 AI 响应的流式数据（逐段返回）
     *
     * @param message 用户输入的聊天消息
     * @param chatId  聊天会话唯一标识
     * @return 包含流式文本的 Flux（响应式编程模型，支持背压）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(name = "streamApiLimiter", fallbackMethod = "streamFallback")
    public Flux<ServerSentEvent<String>> doChatStream(
            @RequestParam String message,
            @RequestParam String chatId) {
        return chatService.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    // 限流 fallback 方法
    public Flux<ServerSentEvent<String>> streamFallback(String message, String chatId, Exception e) {
        return Flux.just(ServerSentEvent.<String>builder()
                .data(BaseResponse.error(1002, "当前请求过多，请稍后再试").getMessage())
                .build());
    }

    /**
     * 流式聊天接口
     * 符合 Server-Sent Events 规范的流式响应，包含完整的 SSE 协议字段
     *
     * @param message 用户输入的聊天消息
     * @param chatId  聊天会话唯一标识
     * @return 包装为 ServerSentEvent 的 Flux 流（包含事件 ID、类型等元数据）
     */
    @GetMapping(value = "/chat/stream-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BaseResponse<String>>> doChatStreamSse(
            @RequestParam String message,
            @RequestParam String chatId) {
        return chatService.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.success(chunk))
                        .build())
                .onErrorReturn(ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.businessError("流式响应异常"))
                        .build());
    }

    /**
     * 流式聊天接口（基于 SseEmitter）
     * 适用于传统 MVC 架构的 SSE 流式响应，通过回调方式推送数据
     *
     * @param message 用户输入的聊天消息
     * @param chatId  聊天会话唯一标识
     * @return SseEmitter 实例（用于异步推送数据）
     */
    @GetMapping("/chat/stream-sse-emitter")
    public SseEmitter doChatStreamSseEmitter(@RequestParam String message, @RequestParam String chatId) {
        // 设置超时时间（3分钟），避免连接长期闲置
        SseEmitter sseEmitter = new SseEmitter(180000L);
        // 订阅响应式数据流，通过 SseEmitter 推送数据
        chatService.doChatByStream(message, chatId)
                .subscribe(
                        chunk -> {
                            try {
                                sseEmitter.send(BaseResponse.success(chunk));
                            } catch (IOException e) {
                                sseEmitter.completeWithError(e);  // 推送失败时标记错误
                            }
                        },
                        // 发生异常时：标记错误并结束
                        error -> {
                            try {
                                sseEmitter.send(BaseResponse.businessError("流式推送异常：" + error.getMessage()));
                            } catch (IOException e) {
                                // 忽略发送错误
                            }
                            sseEmitter.completeWithError(error);
                        },
                        // 流结束时：正常完成连接
                        () -> {
                            try {
                                sseEmitter.send(BaseResponse.success(null, "流式响应结束"));
                            } catch (IOException e) {
                                // 忽略发送错误
                            }
                            sseEmitter.complete();
                        }
                );

        // 注册连接超时/完成的回调
        sseEmitter.onTimeout(() -> {
            try {
                sseEmitter.send(BaseResponse.error(1003, "连接超时"));
            } catch (IOException e) {
                // 忽略超时发送错误
            }
            sseEmitter.complete();
        });

        sseEmitter.onCompletion(() -> {
            // 在此处添加资源释放逻辑，如取消订阅、清理会话等
        });

        return sseEmitter;
    }

    /**
     * 结构化报告生成接口
     * 返回 AI 生成的结构化报告数据（而非纯文本）
     *
     * @param message 用户输入的报告生成指令
     * @param chatId  聊天会话唯一标识
     * @return 包含结构化报告的统一响应对象
     */
    @GetMapping("/chat/report")
    public BaseResponse<ReportVO> doChatReport(@RequestParam String message, @RequestParam String chatId) {
        try {
            ReportVO reportVO = chatService.doChatWithStructuredOutput(message, chatId);
            return BaseResponse.success(reportVO, "报告生成成功");
        } catch (Exception e) {
            return BaseResponse.businessError("报告生成失败：" + e.getMessage());
        }
    }

    /**
     * 带工具调用的流式聊天接口
     * AI 可调用外部工具，并流式返回结果
     *
     * @param message 用户输入的消息（可能触发工具调用）
     * @param chatId  聊天会话唯一标识
     * @return 包装为 ServerSentEvent 的工具调用结果流
     */
    @GetMapping(value = "/chat/tools", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BaseResponse<String>>> doChatWithTools(
            @RequestParam String message,
            @RequestParam String chatId) {
        return chatService.doChatByStreamWithTool(message, chatId)
                .map(chunk -> ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.success(chunk))
                        .build())
                .onErrorReturn(ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.businessError("工具调用流式响应异常"))
                        .build());
    }

    /**
     * 带工具调用及回调的流式聊天接口
     * 支持工具调用后的回调处理（如多轮工具交互），并流式返回最终结果
     *
     * @param message 用户输入的消息
     * @param chatId  聊天会话唯一标识
     * @return 包装为 ServerSentEvent 的工具调用+回调结果流
     */
    @GetMapping(value = "/chat/tools/callback", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BaseResponse<String>>> doChatWithToolsAndCallback(
            @RequestParam String message,
            @RequestParam String chatId) {
        return chatService.doChatByStreamWithToolAndCallback(message, chatId)
                .map(chunk -> ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.success(chunk))
                        .build())
                .onErrorReturn(ServerSentEvent.<BaseResponse<String>>builder()
                        .data(BaseResponse.businessError("工具调用回调流式响应异常"))
                        .build());
    }
}