package com.ct.ai.agent.agent.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工具调用配置参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.tool")
public class ToolCallProperties {
    // 工具执行超时时间（毫秒），默认30秒
    private long timeoutMs = 30000;
    // 连续失败最大次数（超过则降级），默认3次
    private int maxConsecutiveFailures = 3;
    // 降级时是否返回缓存结果（true：返回缓存，false：提示暂不可用）
    private boolean fallbackToCache = true;
}