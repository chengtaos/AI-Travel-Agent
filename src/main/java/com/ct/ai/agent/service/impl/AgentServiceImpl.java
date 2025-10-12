package com.ct.ai.agent.service.impl;

import com.ct.ai.agent.agent.AgentState;
import com.ct.ai.agent.agent.MyAgent;
import com.ct.ai.agent.dto.ChatMessageDTO;
import com.ct.ai.agent.service.AgentService;
import com.ct.ai.agent.vo.AgentRequestVO;
import com.ct.ai.agent.vo.AgentResponseVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体服务实现类
 * 实现AgentService接口，封装智能体的核心交互逻辑（同步/流式请求、状态管理、连接维护）
 */
@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    // 注入智能体实例（实际处理智能体逻辑的核心组件）
    @Resource
    private MyAgent myAgent;

    // 线程安全的Map：存储活跃的SSE连接（key=会话ID，value=SSE发射器），用于手动关闭连接
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();


    /**
     * 基础同步请求：执行简单智能体任务
     * 适用于无需额外配置的场景，直接传入用户prompt获取结果
     */
    @Override
    public String executeTask(String prompt) {
        log.info("【基础同步】执行智能体任务，prompt长度：{}字符", prompt.trim().length());

        try {
            myAgent.reset(); // 重置智能体状态，避免上一次任务残留影响
            String result = myAgent.run(prompt); // 调用智能体执行任务，获取完整结果
            log.info("【基础同步】任务执行完成，智能体状态：{}", myAgent.getState());
            return result;

        } catch (Exception e) {
            log.error("【基础同步】任务执行失败", e);
            throw new RuntimeException("执行失败：" + e.getMessage(), e); // 抛出异常，由Controller层统一处理
        }
    }


    /**
     * 高级同步请求：支持自定义配置的智能体任务
     * 可传入会话ID、最大步骤数等配置，返回包含执行耗时、会话信息的完整响应
     */
    @Override
    public AgentResponseVO executeAdvancedTask(AgentRequestVO request) {
        log.info("【高级同步】执行智能体任务，请求参数：{}", request);

        try {
            long startTime = System.currentTimeMillis(); // 记录任务开始时间，用于计算耗时
            myAgent.reset(); // 重置智能体状态
            applyCustomParameters(request); // 应用自定义配置（如最大步骤数、系统提示）

            String result = myAgent.run(request.getPrompt()); // 执行任务
            long executionTime = System.currentTimeMillis() - startTime; // 计算执行耗时
            saveExecutionHistory(request, result); // 保存任务执行历史

            // 构建包含详细信息的成功响应
            AgentResponseVO response = AgentResponseVO.success(
                    result,
                    myAgent.getState(),
                    request.getSessionId() != null ? request.getSessionId() : "none",
                    executionTime
            );
            log.info("【高级同步】任务执行完成，状态：{}，耗时：{}ms", myAgent.getState(), executionTime);
            return response;

        } catch (Exception e) {
            log.error("【高级同步】任务执行失败", e);
            return AgentResponseVO.error("执行失败：" + e.getMessage(), myAgent.getState()); // 返回错误响应
        }
    }


    /**
     * 基础流式请求：逐段返回智能体结果
     * 生成唯一会话ID，维护活跃连接，支持自动清理超时/完成的连接
     */
    @Override
    public SseEmitter executeTaskStream(String prompt) {
        log.info("【基础流式】执行智能体任务，prompt长度：{}字符", prompt.trim().length());
        String sessionId = UUID.randomUUID().toString(); // 生成唯一会话ID，标识当前流式连接

        try {
            myAgent.reset();
            SseEmitter emitter = myAgent.runStream(prompt);
            activeEmitters.put(sessionId, emitter); // 将连接存入活跃列表

            // 连接完成后：从活跃列表移除，避免内存泄漏
            emitter.onCompletion(() -> {
                activeEmitters.remove(sessionId);
                log.debug("【基础流式】会话完成，移除连接：{}", sessionId);
            });

            // 连接超时后：移除连接
            emitter.onTimeout(() -> {
                activeEmitters.remove(sessionId);
                log.warn("【基础流式】会话超时，移除连接：{}", sessionId);
            });

            // 连接出错后：移除连接并打印错误日志
            emitter.onError(e -> {
                activeEmitters.remove(sessionId);
                log.error("【基础流式】会话出错，移除连接：{}", sessionId, e);
            });

            return emitter;

        } catch (Exception e) {
            log.error("【基础流式】任务初始化失败", e);
            SseEmitter errorEmitter = new SseEmitter(5000L); // 5秒超时的错误发射器
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("初始化失败: " + e.getMessage()));
                errorEmitter.complete(); // 完成连接
            } catch (Exception ex) {
                log.error("【基础流式】发送错误消息失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 高级流式请求：支持自定义配置的流式任务
     * 可复用传入的会话ID，或自动生成新ID，适配长对话场景
     */
    @Override
    public SseEmitter executeAdvancedTaskStream(AgentRequestVO request) {
        log.info("【高级流式】执行智能体任务，请求参数：{}", request);
        // 优先使用请求中的会话ID，无则生成新ID（支持会话复用）
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        try {
            myAgent.reset();
            applyCustomParameters(request); // 应用自定义配置
            SseEmitter emitter = myAgent.runStream(request.getPrompt());
            activeEmitters.put(sessionId, emitter);

            // 连接生命周期回调：完成/超时/错误时移除连接
            emitter.onCompletion(() -> {
                activeEmitters.remove(sessionId);
                log.debug("【高级流式】会话完成，移除连接：{}", sessionId);
            });
            emitter.onTimeout(() -> {
                activeEmitters.remove(sessionId);
                log.warn("【高级流式】会话超时，移除连接：{}", sessionId);
            });
            emitter.onError(e -> {
                activeEmitters.remove(sessionId);
                log.error("【高级流式】会话出错，移除连接：{}", sessionId, e);
            });

            return emitter;

        } catch (Exception e) {
            log.error("【高级流式】任务初始化失败", e);
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("初始化失败: " + e.getMessage()));
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("【高级流式】发送错误消息失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 查询智能体当前状态
     * 返回智能体名称、状态、活跃连接数等核心信息
     */
    @Override
    public AgentResponseVO getAgentStatus() {
        Map<String, Object> statusMap = new HashMap<>();
        // 封装智能体状态信息
        statusMap.put("name", myAgent.getName()); // 智能体名称
        statusMap.put("state", myAgent.getState().toString()); // 智能体状态（如IDLE/RUNNING）
        statusMap.put("currentStep", myAgent.getCurrentStep()); // 当前执行步骤
        statusMap.put("maxSteps", myAgent.getMaxSteps()); // 最大支持步骤数
        statusMap.put("availableTools", myAgent.getAvailableToolsList().size()); // 可用工具数量
        statusMap.put("activeConnections", activeEmitters.size()); // 活跃SSE连接数

        // 构建状态响应
        return new AgentResponseVO()
                .setStatus("success")
                .setResult(statusMap.toString())
                .setAgentState(myAgent.getState().toString());
    }


    /**
     * 重置智能体状态
     * 清空任务残留，恢复智能体到初始可用状态
     */
    @Override
    public AgentResponseVO resetAgent() {
        try {
            myAgent.reset();
            return AgentResponseVO.success("智能体已重置", myAgent.getState(), "none");
        } catch (Exception e) {
            log.error("【重置智能体】失败", e);
            return AgentResponseVO.error("重置失败: " + e.getMessage(), myAgent.getState());
        }
    }


    /**
     * 手动关闭指定会话的流式连接
     * 根据会话ID从活跃列表中找到连接，发送关闭事件并完成连接
     */
    @Override
    public AgentResponseVO closeStream(String sessionId) {
        if (activeEmitters.containsKey(sessionId)) {
            SseEmitter emitter = activeEmitters.get(sessionId);
            try {
                // 发送关闭事件，告知前端连接已手动关闭
                emitter.send(SseEmitter.event().name("close").data("手动关闭连接"));
                emitter.complete(); // 完成连接
                activeEmitters.remove(sessionId); // 从活跃列表移除
                return AgentResponseVO.success("流式连接已关闭", AgentState.IDLE, sessionId);

            } catch (IOException e) {
                log.error("【关闭流式连接】失败，会话ID：{}", sessionId, e);
                return AgentResponseVO.error("关闭连接失败: " + e.getMessage(), myAgent.getState());
            }
        } else {
            // 会话ID不存在，返回警告响应
            return AgentResponseVO.warning("指定的会话ID不存在或已关闭");
        }
    }


    /**
     * 保存智能体交互历史
     * （当前为简化实现，仅打印日志；实际项目需对接数据库/缓存存储）
     */
    @Override
    public boolean saveAgentHistory(String sessionId, ChatMessageDTO message) {
        log.info("【保存历史】sessionId={}, 消息内容：{}", sessionId, message);
        return true; // 模拟保存成功
    }


    /**
     * 私有工具方法：将请求中的自定义参数应用到智能体
     * 如设置最大步骤数、自定义系统提示
     */
    private void applyCustomParameters(AgentRequestVO request) {
        // 设置最大步骤数（需大于0才生效）
        if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
            myAgent.setMaxSteps(request.getMaxSteps());
        }
        // 设置自定义系统提示（非空才生效）
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            myAgent.setSystemPrompt(request.getSystemPrompt());
        }
    }


    /**
     * 私有工具方法：保存高级同步任务的执行历史
     * 封装历史消息DTO，调用saveAgentHistory存储
     */
    private void saveExecutionHistory(AgentRequestVO request, String result) {
        ChatMessageDTO messageDTO = new ChatMessageDTO()
                .setSessionId(request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString())
                .setRole("assistant") // 角色为"助手"（智能体回复）
                .setContent(result) // 消息内容为智能体执行结果
                .setCreateTime(LocalDateTime.now()) // 消息创建时间
                .setType("text"); // 消息类型为文本

        saveAgentHistory(messageDTO.getSessionId(), messageDTO); // 调用保存历史方法
    }
}