package com.ct.ai.agent.agent.factory;

import com.ct.ai.agent.agent.MyAgent;
import com.ct.ai.agent.agent.property.ToolCallProperties;
import com.ct.ai.agent.util.SessionContextManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Agent工厂类：负责创建MyAgent实例，手动传入动态参数chatId
 */
@Component
public class AgentFactory {

    private final ToolCallback[] allTools;
    private final ChatModel dashscopeChatModel;
    private final ToolCallProperties toolCallProperties;
    private final SessionContextManager contextManager;

    public AgentFactory(
            @Qualifier("allTools") ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            ToolCallProperties toolCallProperties,
            SessionContextManager contextManager) {
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
        this.toolCallProperties = toolCallProperties;
        this.contextManager = contextManager;
    }

    /**
     * 创建MyAgent实例（手动传入动态chatId）
     *
     * @param chatId 会话唯一标识（动态生成）
     * @return MyAgent实例
     */
    public MyAgent createMyAgent(String chatId) {
        // 手动传入动态参数chatId，其他依赖从工厂的成员变量获取
        return new MyAgent(
                allTools,
                dashscopeChatModel,
                toolCallProperties,
                contextManager,
                chatId
        );
    }
}