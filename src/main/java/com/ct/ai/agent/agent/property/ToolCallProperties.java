package com.ct.ai.agent.agent.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工具调用配置参数（含会话上下文管理）
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.tool")
public class ToolCallProperties {
    // 工具执行超时时间（毫秒），默认30秒
    private long timeoutMs = 30000;

    // 连续失败最大次数（超过则降级），默认3次
    private int maxConsecutiveFailures = 3;

    // 降级时是否返回缓存结果
    private boolean fallbackToCache = true;

    // 会话上下文TTL（小时），默认24小时无活动自动过期
    private int sessionTtlHours = 24;

    // 单会话最大消息数（控制上下文大小），默认100轮
    private int maxSessionMessages = 100;
}