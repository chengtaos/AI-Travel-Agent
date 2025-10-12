package com.ct.ai.agent.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * 通用 Redis 序列化器，支持 Message 类型的多态序列化，也支持其他 Object 类型
 *
 * @param <T> 序列化的对象类型
 */
public class MyRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> clazz;

    public MyRedisSerializer(Class<T> clazz) {
        this.clazz = clazz;
        this.objectMapper = new ObjectMapper();
        configureObjectMapper();
    }

    private void configureObjectMapper() {
        // 如果是 Message 类型，注册自定义序列化/反序列化器
        if (Message.class.isAssignableFrom(clazz)) {
            SimpleModule messageModule = new SimpleModule();
            messageModule.addSerializer(Message.class, new MessageSerializer());
            messageModule.addDeserializer(Message.class, new MessageDeserializer());
            objectMapper.registerModule(messageModule);
        }

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(t);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize JSON to object", e);
        }
    }

    // ========== Message 专用序列化逻辑 ==========

    private static class MessageSerializer extends JsonSerializer<Message> {
        @Override
        public void serialize(Message message, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("messageType", message.getMessageType().getValue().toUpperCase());
            gen.writeStringField("text", message.getText());
            gen.writeEndObject();
        }
    }

    private static class MessageDeserializer extends JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
            JsonNode rootNode = jp.readValueAsTree();

            JsonNode typeNode = rootNode.get("messageType");
            if (typeNode == null) {
                throw new JsonParseException(jp, "Missing required field 'messageType' in Message JSON");
            }
            String messageType = typeNode.asText();

            JsonNode textNode = rootNode.get("text");
            if (textNode == null) {
                throw new JsonParseException(jp, "Missing required field 'text' in Message JSON");
            }
            String text = textNode.asText();

            return switch (messageType) {
                case "USER" -> new UserMessage(text);
                case "ASSISTANT" -> new AssistantMessage(text);
                case "SYSTEM" -> new SystemMessage(text);
                default -> throw new JsonParseException(jp, "Unsupported messageType: " + messageType
                        + ". Supported types: USER, ASSISTANT, SYSTEM");
            };
        }
    }
}