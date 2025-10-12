package com.ct.ai.agent.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * Redis 消息对象序列化器
 * 用于 Spring AI 的 Message 接口及其实现类的 Redis 存储序列化
 * 解决接口类型序列化时的多态识别问题，确保序列化/反序列化过程中类型信息不丢失
 */
public class MessageRedisSerializer implements RedisSerializer<Message> {

    /**
     * Jackson 核心处理对象，负责 JSON 序列化/反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造器：初始化 ObjectMapper 并配置序列化规则
     */
    public MessageRedisSerializer() {
        this.objectMapper = new ObjectMapper();
        configureObjectMapper();
    }

    /**
     * 配置 ObjectMapper：注册自定义序列化/反序列化逻辑，设置全局序列化特性
     */
    private void configureObjectMapper() {
        // 注册自定义模块，绑定 Message 类型与自定义序列化器
        SimpleModule messageModule = new SimpleModule();
        messageModule.addSerializer(Message.class, new MessageSerializer());
        messageModule.addDeserializer(Message.class, new MessageDeserializer());
        objectMapper.registerModule(messageModule);

        // 配置反序列化特性：忽略未知字段（避免因类结构变更导致反序列化失败）
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 配置序列化特性：允许空对象序列化（避免空对象抛出异常）
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * 将 Message 对象序列化为字节数组（存储到 Redis 时使用）
     *
     * @param message 待序列化的消息对象
     * @return 序列化后的字节数组；若输入为 null 则返回 null
     * @throws SerializationException 序列化失败时抛出（包装原始异常）
     */
    @Override
    public byte[] serialize(Message message) throws SerializationException {
        if (message == null) {
            return null; // 空对象直接返回 null
        }
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            // 转换为 Redis 序列化框架的异常类型，便于上层统一处理
            throw new SerializationException("Failed to serialize Message to JSON", e);
        }
    }

    /**
     * 将字节数组反序列化为 Message 对象（从 Redis 读取时使用）
     *
     * @param bytes 待反序列化的字节数组（JSON 字符串的字节形式）
     * @return 反序列化后的 Message 对象；若输入为空则返回 null
     * @throws SerializationException 反序列化失败时抛出（包装原始异常）
     */
    @Override
    public Message deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null; // 空字节数组直接返回 null
        }
        try {
            return objectMapper.readValue(bytes, Message.class);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize JSON to Message", e);
        }
    }

    /**
     * 自定义 Message 序列化器
     * 负责将 Message 实现类序列化为包含类型标识的 JSON 结构
     */
    private static class MessageSerializer extends JsonSerializer<Message> {

        /**
         * 序列化逻辑：将 Message 转换为 JSON 对象
         *
         * @param message     待序列化的消息对象
         * @param gen         JSON 生成器（用于构建 JSON 结构）
         * @param serializers 序列化上下文（暂未使用）
         * @throws IOException JSON 生成过程中的 IO 异常
         */
        @Override
        public void serialize(Message message, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            // 写入消息类型标识（用于反序列化时识别具体实现类）
            // 从 Message 接口的 getMessageType() 获取类型，统一转为大写便于解析
            gen.writeStringField("messageType", message.getMessageType().getValue().toUpperCase());
            // 写入消息内容
            gen.writeStringField("text", message.getText());
            gen.writeEndObject();
        }
    }

    /**
     * 自定义 Message 反序列化器
     * 根据 JSON 中的类型标识（messageType）反序列化为对应的 Message 实现类
     */
    private static class MessageDeserializer extends JsonDeserializer<Message> {

        /**
         * 反序列化逻辑：从 JSON 解析出 Message 对象
         *
         * @param jp  JSON 解析器（用于读取 JSON 内容）
         * @param ctx 反序列化上下文（暂未使用）
         * @return 具体的 Message 实现类（UserMessage/AssistantMessage/SystemMessage）
         * @throws IOException JSON 解析过程中的 IO 异常
         */
        @Override
        public Message deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
            JsonNode rootNode = jp.readValueAsTree();

            // 解析消息类型（必须字段，用于确定具体实现类）
            JsonNode typeNode = rootNode.get("messageType");
            if (typeNode == null) {
                throw new JsonParseException(jp, "Missing required field 'messageType' in Message JSON");
            }
            String messageType = typeNode.asText();

            // 解析消息内容（必须字段）
            JsonNode textNode = rootNode.get("text");
            if (textNode == null) {
                throw new JsonParseException(jp, "Missing required field 'text' in Message JSON");
            }
            String text = textNode.asText();

            // 根据消息类型创建对应实现类
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