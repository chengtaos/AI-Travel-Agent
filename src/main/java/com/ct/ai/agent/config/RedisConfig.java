package com.ct.ai.agent.config;

import com.ct.ai.agent.util.MyRedisSerializer;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Message> messageRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Message> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new MyRedisSerializer<>(Message.class));

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new MyRedisSerializer<>(Message.class));

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> userRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new MyRedisSerializer<>(Object.class)); // 通用 Object 序列化

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new MyRedisSerializer<>(Object.class));

        template.afterPropertiesSet();
        return template;
    }
}