package com.ct.ai.agent.util;

import com.ct.ai.agent.dao.ChatMessage;
import org.springframework.ai.chat.messages.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * 消息转换工具类
 * 负责 Spring AI 框架的 Message 与数据库实体 ChatMessage 之间的双向转换
 * 解决框架消息模型与持久化存储模型的适配问题，确保消息数据在内存与数据库间正确流转
 */
public class MessageConverter {

    /**
     * 将 Spring AI 的 Message 转换为数据库实体 ChatMessage
     * 用于将内存中的对话消息持久化到数据库时的格式转换
     *
     * @param message        Spring AI 框架的消息对象（可能是 UserMessage/AssistantMessage 等）
     * @param conversationId 对话唯一标识（用于关联同一场对话的所有消息）
     * @return 数据库实体 ChatMessage（包含消息内容、类型、元数据及时间戳）
     * @throws IllegalArgumentException 当输入 message 为 null 时抛出
     */
    public static ChatMessage toChatMessage(Message message, String conversationId) {
        // 校验输入合法性：避免空消息导致的转换异常
        if (message == null) {
            throw new IllegalArgumentException("转换失败：输入的 Message 不能为 null");
        }

        // 构建数据库实体：复制消息核心字段，并补充持久化所需的元数据（对话ID、时间戳）
        return ChatMessage.builder()
                .conversationId(conversationId) // 关联对话ID
                .messageType(message.getMessageType()) // 消息类型（USER/ASSISTANT等）
                .content(message.getText()) // 消息文本内容
                .metadata(message.getMetadata() != null ? message.getMetadata() : Collections.emptyMap()) // 元数据（避免null）
                .createTime(LocalDateTime.now()) // 创建时间（持久化时的当前时间）
                .updateTime(LocalDateTime.now()) // 更新时间（初始与创建时间一致）
                .build();
    }

    /**
     * 将数据库实体 ChatMessage 转换为 Spring AI 的 Message
     * 用于从数据库读取历史消息后，转换为框架可识别的消息格式
     *
     * @param chatMessage 数据库中存储的消息实体
     * @return Spring AI 框架的 Message 实现类（根据消息类型动态创建对应实例）
     * @throws IllegalArgumentException 当输入 chatMessage 为 null 或类型不支持时抛出
     */
    public static Message toMessage(ChatMessage chatMessage) {
        // 校验输入合法性
        if (chatMessage == null) {
            throw new IllegalArgumentException("转换失败：输入的 ChatMessage 不能为 null");
        }

        // 提取数据库实体中的核心字段
        MessageType messageType = chatMessage.getMessageType();
        String text = chatMessage.getContent();
        Map<String, Object> metadata = chatMessage.getMetadata() != null ? chatMessage.getMetadata() : Collections.emptyMap();

        // 根据消息类型创建对应的 Message 实现类
        return switch (messageType) {
            case USER -> new UserMessage(text); // 用户消息
            case ASSISTANT -> new AssistantMessage(text, metadata); // AI回复：包含文本和元数据
            case SYSTEM -> new SystemMessage(text); // 系统消息
            case TOOL -> new ToolResponseMessage(Collections.emptyList(), metadata); // 工具响应消息：默认空内容列表+元数据
            default -> throw new IllegalArgumentException("不支持的消息类型：" + messageType);
        };
    }
}