package com.ct.ai.agent.llm.chatmemory;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis 的聊天记忆实现
 * 用于持久化存储对话历史消息，支持添加、查询、清空对话记忆，适配 Spring AI 的 ChatMemory 接口规范
 */
@Component
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    /**
     * Redis 存储键前缀，用于区分不同类型的缓存数据，避免键冲突
     * 最终存储键格式：chatmemory:{conversationId}
     */
    private static final String REDIS_KEY_PREFIX = "chatmemory:";

    /**
     * 每次查询返回的最大消息条数（最近的 N 条消息）
     */
    private static final int MAX_HISTORY_MESSAGE_COUNT = 10;

    /**
     * Redis 操作模板，已配置自定义 Message 序列化器（MessageRedisSerializer）
     * 负责执行 Redis 底层读写操作
     */
    private final RedisTemplate<String, Message> redisTemplate;

    /**
     * 向对话记忆中添加消息列表
     * 消息会追加到 Redis 列表的尾部，保持时间顺序
     *
     * @param conversationId 对话唯一标识（用于区分不同对话）
     * @param messages       待添加的消息列表（通常是用户消息或AI回复）
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String redisKey = buildRedisKey(conversationId);
        // 将消息列表追加到 Redis 列表尾部（rightPushAll 批量添加）
        redisTemplate.opsForList().rightPushAll(redisKey, messages);
    }

    /**
     * 获取对话的历史消息（最近的 N 条）
     * 从 Redis 列表中读取尾部的最新消息，避免返回过多历史导致性能问题
     *
     * @param conversationId 对话唯一标识
     * @return 最近的历史消息列表（最多 MAX_HISTORY_MESSAGE_COUNT 条），若无消息则返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        String redisKey = buildRedisKey(conversationId);
        // 若列表长度不足 N 条，则返回实际所有消息
        List<Message> historyMessages = redisTemplate.opsForList().range(
                redisKey,
                -MAX_HISTORY_MESSAGE_COUNT,
                -1
        );
        // 处理 Redis 返回 null 的情况（键不存在或无数据），返回空列表而非 null
        return historyMessages != null ? historyMessages : Collections.emptyList();
    }

    /**
     * 清空对话的历史消息
     * 直接删除 Redis 中对应的键，彻底清除该对话的所有记忆
     *
     * @param conversationId 对话唯一标识
     */
    @Override
    public void clear(String conversationId) {
        String redisKey = buildRedisKey(conversationId);
        redisTemplate.delete(redisKey);
    }

    /**
     * 构建 Redis 存储键
     * 封装键前缀与对话ID的拼接逻辑，避免重复代码
     *
     * @param conversationId 对话唯一标识
     * @return 完整的 Redis 存储键
     */
    private String buildRedisKey(String conversationId) {
        return REDIS_KEY_PREFIX + conversationId;
    }
}