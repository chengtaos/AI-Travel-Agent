package com.ct.ai.agent.llm.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ct.ai.agent.dao.ChatMessage;
import com.ct.ai.agent.mapper.ChatMessageRepository;
import com.ct.ai.agent.util.MessageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于数据库的聊天记忆实现
 * 实现 Spring AI 的 ChatMemory 接口，负责将对话历史消息持久化到 PostgreSQL 数据库
 * 与 RedisChatMemory 功能一致，可根据场景选择使用（数据库适合持久化存储，Redis 适合高频访问场景）
 * 使用 MyBatis-Plus 作为 ORM 框架简化数据库操作
 */
@Component
@RequiredArgsConstructor
public class DatabaseChatMemory implements ChatMemory {

    /**
     * 聊天消息数据访问层（MyBatis-Plus Mapper）
     * 负责执行数据库 CRUD 操作
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 每次查询返回的最大历史消息条数
     * 限制数量可减少大模型输入长度，提升处理效率并降低成本
     */
    private static final int MAX_HISTORY_MESSAGE_COUNT = 10;

    /**
     * 向对话记忆中添加消息列表
     * 将 Spring AI 的 Message 转换为数据库实体 ChatMessage 后批量保存
     *
     * @param conversationId 对话唯一标识（用于关联同一场对话的所有消息）
     * @param messages       待保存的消息列表（用户消息/AI回复等）
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        // 1. 将 Message 转换为数据库实体 ChatMessage（包含对话ID和时间戳等元数据）
        List<ChatMessage> chatMessages = messages.stream()
                .map(message -> MessageConverter.toChatMessage(message, conversationId))
                .collect(Collectors.toList());

        // 2. 批量保存到数据库（指定批次大小为集合长度，优化插入性能）
        chatMessageRepository.saveBatch(chatMessages, chatMessages.size());
    }

    /**
     * 获取对话的历史消息（最近的 N 条）
     * 从数据库查询并转换为 Spring AI 可识别的 Message 格式，按时间正序返回
     *
     * @param conversationId 对话唯一标识
     * @return 按时间顺序排列的历史消息列表（最多 MAX_HISTORY_MESSAGE_COUNT 条），无消息时返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        // 1. 构建查询条件：按对话ID过滤，按创建时间倒序（最新的消息在前），限制最大条数
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + MAX_HISTORY_MESSAGE_COUNT); // 数据库方言兼容：PostgreSQL 用 LIMIT，其他库可调整

        // 2. 执行查询，获取最近的 N 条消息（按时间倒序）
        List<ChatMessage> chatMessages = chatMessageRepository.list(queryWrapper);

        // 3. 反转列表，将消息按时间正序排列（最早的在前，符合对话流顺序）
        if (!chatMessages.isEmpty()) {
            Collections.reverse(chatMessages);
        }

        // 4. 转换为 Spring AI 的 Message 类型并返回
        return chatMessages.stream()
                .map(MessageConverter::toMessage)
                .collect(Collectors.toList());
    }

    /**
     * 清空对话的历史消息
     * 根据对话ID删除数据库中对应的所有消息记录
     *
     * @param conversationId 对话唯一标识
     */
    @Override
    public void clear(String conversationId) {
        // 构建删除条件：按对话ID精确匹配
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getConversationId, conversationId);

        // 执行删除操作
        chatMessageRepository.remove(queryWrapper);
    }
}