package com.ct.ai.agent.service;

import com.ct.ai.agent.dto.AgentRequestDTO;
import com.ct.ai.agent.dto.AgentResponseDTO;
import com.ct.ai.agent.dto.ChatMessageDTO;
import com.ct.ai.agent.dto.AgentRequestDTO;
import com.ct.ai.agent.dto.AgentResponseDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能体服务接口
 * 提供智能体相关的服务功能
 */
public interface AgentService {

    /**
     * 执行智能体任务（同步模式）
     *
     * @param prompt 用户提示词
     * @return 执行结果
     */
    String executeTask(String prompt,String sessionId);

    /**
     * 执行智能体任务（高级同步模式）
     *
     * @param request 智能体请求对象
     * @return 执行结果
     */
    AgentResponseDTO executeAdvancedTask(AgentRequestDTO request);

    /**
     * 执行智能体任务（流式模式）
     *
     * @param prompt 用户提示词
     * @return SSE事件发射器
     */
    SseEmitter executeTaskStream(String prompt,String sessionId);

    /**
     * 执行智能体任务（高级流式模式）
     *
     * @param request 智能体请求对象
     * @return SSE事件发射器
     */
    SseEmitter executeAdvancedTaskStream(AgentRequestDTO request);

    /**
     * 获取智能体状态
     *
     * @return 状态信息
     */
    AgentResponseDTO getAgentStatus();

    /**
     * 重置智能体状态
     *
     * @return 操作结果
     */
    AgentResponseDTO resetAgent();

    AgentResponseDTO getAgentStatus(String sessionId);

    AgentResponseDTO resetAgent(String sessionId);

    /**
     * 关闭流式连接
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    AgentResponseDTO closeStream(String sessionId);

    /**
     * 保存智能体执行历史
     *
     * @param sessionId 会话ID
     * @param message   消息内容
     */
    void saveAgentHistory(String sessionId, ChatMessageDTO message);
}