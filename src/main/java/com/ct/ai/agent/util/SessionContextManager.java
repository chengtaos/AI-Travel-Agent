package com.ct.ai.agent.util;

import com.ct.ai.agent.agent.property.ToolCallProperties;
import com.ct.ai.agent.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话上下文管理器：处理上下文的存储、读取、更新和清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionContextManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ToolCallProperties toolProperties;

    /**
     * 从Redis获取会话上下文
     */
    @SuppressWarnings("unchecked")
    public List<Message> getContext(String chatId) {
        String redisKey = getRedisKey(chatId);
        List<Message> messages = (List<Message>) redisTemplate.opsForValue().get(redisKey);

        // 重置TTL（每次访问延长有效期）
        if (messages != null) {
            redisTemplate.expire(redisKey, toolProperties.getSessionTtlHours(), TimeUnit.HOURS);
            return messages;
        }
        return new ArrayList<>();
    }

    /**
     * 保存会话上下文到Redis（自动控制大小和TTL）
     */
    public void saveContext(String chatId, List<Message> messages) {
        if (chatId == null || messages == null) {
            log.warn("chatId或消息列表为空，不保存上下文");
            return;
        }

        String redisKey = getRedisKey(chatId);
        // 控制单会话最大消息数（超过则截断最旧的消息）
        List<Message> limitedMessages = limitContextSize(messages);

        // 保存并设置TTL（默认24小时）
        redisTemplate.opsForValue().set(
                redisKey,
                limitedMessages,
                toolProperties.getSessionTtlHours(),
                TimeUnit.HOURS
        );
        log.debug("会话[{}]上下文已保存，消息数：{}，TTL：{}小时",
                chatId, limitedMessages.size(), toolProperties.getSessionTtlHours());
    }

    /**
     * 清理指定会话上下文
     */
    public void clearContext(String chatId) {
        redisTemplate.delete(getRedisKey(chatId));
        log.debug("会话[{}]上下文已手动清理", chatId);
    }

    /**
     * 限制单会话上下文大小（保留最新的N条消息）
     */
    private List<Message> limitContextSize(List<Message> messages) {
        int maxSize = toolProperties.getMaxSessionMessages();
        if (messages.size() <= maxSize) {
            return messages;
        }
        // 超过最大限制时，删除最旧的消息
        List<Message> truncated = messages.subList(messages.size() - maxSize, messages.size());
        log.debug("会话上下文消息数超过{}，已截断为{}条", maxSize, truncated.size());
        return truncated;
    }

    /**
     * 生成Redis存储的key
     */
    private String getRedisKey(String chatId) {
        return RedisKeyConstant.SESSION_CONTEXT_PREFIX + chatId;
    }
}