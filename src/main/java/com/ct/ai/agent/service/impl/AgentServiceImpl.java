package com.ct.ai.agent.service.impl;

import com.ct.ai.agent.agent.AgentState;
import com.ct.ai.agent.agent.MyAgent;
import com.ct.ai.agent.agent.factory.AgentFactory;
import com.ct.ai.agent.dto.ChatMessageDTO;
import com.ct.ai.agent.service.AgentService;
import com.ct.ai.agent.vo.AgentRequestVO;
import com.ct.ai.agent.vo.AgentResponseVO;
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

    // 注入智能体工厂（通过工厂创建MyAgent实例，解决chatId动态传入问题）
    private final AgentFactory agentFactory;

    // 线程安全的Map：存储"会话ID -> MyAgent实例"，管理活跃智能体（避免重复创建）
    private final Map<String, MyAgent> activeAgents = new ConcurrentHashMap<>();

    // 线程安全的Map：存储活跃的SSE连接（key=会话ID，value=SSE发射器）
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    // 构造函数注入工厂（Spring自动装配工厂，工厂已管理MyAgent的固定依赖）
    public AgentServiceImpl(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }


    /**
     * 基础同步请求：执行简单智能体任务
     * 自动生成会话ID，任务完成后销毁智能体（轻量场景）
     */
    @Override
    public String executeTask(String prompt) {
        log.info("【基础同步】执行智能体任务，prompt长度：{}字符", prompt.trim().length());

        // 1. 生成临时会话ID（基础同步场景无需复用会话，任务完成后销毁）
        String sessionId = UUID.randomUUID().toString();
        MyAgent myAgent = null;

        try {
            // 2. 通过工厂创建MyAgent实例（传入动态sessionId=chatId）
            myAgent = agentFactory.createMyAgent(sessionId);
            myAgent.reset(); // 重置智能体状态（避免初始化残留）

            // 3. 执行任务并返回结果
            String result = myAgent.run(prompt);
            log.info("【基础同步】任务执行完成，会话ID：{}，智能体状态：{}", sessionId, myAgent.getState());
            return result;

        } catch (Exception e) {
            log.error("【基础同步】任务执行失败，会话ID：{}", sessionId, e);
            throw new RuntimeException("执行失败：" + e.getMessage(), e);

        } finally {
            // 4. 基础场景无需复用会话，任务完成后移除智能体（释放资源）
            if (myAgent != null) {
                activeAgents.remove(sessionId);
            }
        }
    }


    /**
     * 高级同步请求：支持自定义配置的智能体任务
     * 可复用会话ID（传入则复用已有智能体，无则生成新ID），支持长对话
     */
    @Override
    public AgentResponseVO executeAdvancedTask(AgentRequestVO request) {
        log.info("【高级同步】执行智能体任务，请求参数：{}", request);

        // 1. 处理会话ID：优先使用请求中的ID，无则生成新ID（支持会话复用）
        String sessionId = request.getSessionId() != null ?
                request.getSessionId() : UUID.randomUUID().toString();
        MyAgent myAgent = null;

        try {
            // 2. 获取智能体：已有活跃智能体则复用，无则创建新实例
            myAgent = getOrCreateAgent(sessionId);
            myAgent.reset(); // 重置状态（保留会话上下文，仅重置步骤计数等临时状态）
            applyCustomParameters(request, myAgent); // 应用自定义配置（如最大步骤数）

            // 3. 执行任务并计算耗时
            long startTime = System.currentTimeMillis();
            String result = myAgent.run(request.getPrompt());
            long executionTime = System.currentTimeMillis() - startTime;

            // 4. 保存执行历史（关联会话ID）
            saveExecutionHistory(request, result, sessionId);

            // 5. 构建响应（包含会话ID、耗时等详细信息）
            AgentResponseVO response = AgentResponseVO.success(
                    result,
                    myAgent.getState(),
                    sessionId,
                    executionTime
            );
            log.info("【高级同步】任务执行完成，会话ID：{}，状态：{}，耗时：{}ms",
                    sessionId, myAgent.getState(), executionTime);
            return response;

        } catch (Exception e) {
            log.error("【高级同步】任务执行失败，会话ID：{}", sessionId, e);
            return AgentResponseVO.error("执行失败：" + e.getMessage(),
                    myAgent != null ? myAgent.getState() : AgentState.ERROR);

        }
    }


    /**
     * 基础流式请求：逐段返回智能体结果
     * 生成唯一会话ID，维护活跃连接，任务完成后自动清理
     */
    @Override
    public SseEmitter executeTaskStream(String prompt) {
        log.info("【基础流式】执行智能体任务，prompt长度：{}字符", prompt.trim().length());

        // 1. 生成唯一会话ID（流式场景需绑定连接与智能体）
        String sessionId = UUID.randomUUID().toString();
        MyAgent myAgent = null;
        SseEmitter emitter = null;

        try {
            // 2. 创建智能体与SSE发射器
            myAgent = agentFactory.createMyAgent(sessionId);
            myAgent.reset();
            emitter = myAgent.runStream(prompt);

            // 3. 维护活跃连接与智能体（用于后续关闭/状态查询）
            activeEmitters.put(sessionId, emitter);
            activeAgents.put(sessionId, myAgent);

            // 4. 绑定SSE连接生命周期（完成/超时/错误时清理资源）
            bindEmitterLifecycle(emitter, sessionId);

            log.info("【基础流式】会话初始化完成，会话ID：{}，连接已建立", sessionId);
            return emitter;

        } catch (Exception e) {
            log.error("【基础流式】任务初始化失败，会话ID：{}", sessionId, e);

            // 5. 初始化失败时清理资源，返回错误发射器
            cleanResourceOnError(sessionId, emitter);
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("初始化失败: " + e.getMessage()));
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("【基础流式】发送错误消息失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 高级流式请求：支持自定义配置的流式任务
     * 可复用会话ID（长对话场景），支持自定义系统提示、最大步骤数
     */
    @Override
    public SseEmitter executeAdvancedTaskStream(AgentRequestVO request) {
        log.info("【高级流式】执行智能体任务，请求参数：{}", request);

        // 1. 处理会话ID：优先复用请求中的ID，无则生成新ID
        String sessionId = request.getSessionId() != null ?
                request.getSessionId() : UUID.randomUUID().toString();
        MyAgent myAgent = null;
        SseEmitter emitter = null;

        try {
            // 2. 获取智能体（复用或创建）并应用配置
            myAgent = getOrCreateAgent(sessionId);
            myAgent.reset();
            applyCustomParameters(request, myAgent);

            // 3. 执行流式任务并维护连接
            emitter = myAgent.runStream(request.getPrompt());
            activeEmitters.put(sessionId, emitter);
            bindEmitterLifecycle(emitter, sessionId);

            log.info("【高级流式】会话初始化完成，会话ID：{}，连接已建立", sessionId);
            return emitter;

        } catch (Exception e) {
            log.error("【高级流式】任务初始化失败，会话ID：{}", sessionId, e);

            // 4. 初始化失败时清理资源
            cleanResourceOnError(sessionId, emitter);
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("初始化失败: " + e.getMessage()));
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("【高级流式】发送错误消息失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 查询智能体当前状态（支持指定会话ID，无则返回所有活跃会话状态）
     */
    @Override
    public AgentResponseVO getAgentStatus(String sessionId) {
        Map<String, Object> statusMap = new HashMap<>();

        if (sessionId != null && !sessionId.isEmpty()) {
            // 1. 查询指定会话的智能体状态
            MyAgent myAgent = activeAgents.get(sessionId);
            if (myAgent == null) {
                return AgentResponseVO.warning("指定会话ID不存在或已关闭：" + sessionId);
            }

            // 封装单个会话状态
            statusMap.put("sessionId", sessionId);
            statusMap.put("agentName", myAgent.getName());
            statusMap.put("agentState", myAgent.getState().toString());
            statusMap.put("currentStep", myAgent.getCurrentStep());
            statusMap.put("maxSteps", myAgent.getMaxSteps());
            statusMap.put("availableTools", myAgent.getAvailableToolsList().size());
            statusMap.put("isStreamActive", activeEmitters.containsKey(sessionId));

        } else {
            // 2. 查询所有活跃会话状态（默认场景）
            statusMap.put("totalActiveAgents", activeAgents.size());
            statusMap.put("totalActiveStreams", activeEmitters.size());
            statusMap.put("activeSessions", activeAgents.keySet().stream().toList());
        }

        // 构建状态响应
        return new AgentResponseVO()
                .setStatus("success")
                .setResult(statusMap.toString())
                .setAgentState(activeAgents.isEmpty() ?
                        AgentState.IDLE.toString() : AgentState.RUNNING.toString());
    }


    /**
     * 重置智能体状态（支持指定会话ID）
     */
    @Override
    public AgentResponseVO resetAgent(String sessionId) {
        if (sessionId == null || !activeAgents.containsKey(sessionId)) {
            return AgentResponseVO.warning("指定会话ID不存在或已关闭：" + sessionId);
        }

        try {
            MyAgent myAgent = activeAgents.get(sessionId);
            myAgent.reset();
            log.info("【重置智能体】会话ID：{}，重置完成，当前状态：{}", sessionId, myAgent.getState());
            return AgentResponseVO.success("智能体已重置", myAgent.getState(), sessionId);

        } catch (Exception e) {
            log.error("【重置智能体】失败，会话ID：{}", sessionId, e);
            return AgentResponseVO.error("重置失败: " + e.getMessage(), AgentState.ERROR);
        }
    }


    /**
     * 手动关闭指定会话的流式连接
     */
    @Override
    public AgentResponseVO closeStream(String sessionId) {
        if (!activeEmitters.containsKey(sessionId)) {
            return AgentResponseVO.warning("指定的会话ID不存在或已关闭：" + sessionId);
        }

        SseEmitter emitter = activeEmitters.get(sessionId);
        try {
            // 发送关闭事件并完成连接
            emitter.send(SseEmitter.event()
                    .name("close")
                    .data("手动关闭连接"));
            emitter.complete();

            // 清理资源（智能体+连接）
            activeEmitters.remove(sessionId);
            activeAgents.remove(sessionId);
            log.info("【关闭流式连接】成功，会话ID：{}", sessionId);
            return AgentResponseVO.success("流式连接已关闭", AgentState.IDLE, sessionId);

        } catch (IOException e) {
            log.error("【关闭流式连接】失败，会话ID：{}", sessionId, e);
            return AgentResponseVO.error("关闭连接失败: " + e.getMessage(), AgentState.ERROR);
        }
    }


    /**
     * 保存智能体交互历史（关联会话ID）
     */
    @Override
    public void saveAgentHistory(String sessionId, ChatMessageDTO message) {
        if (sessionId == null || message == null) {
            log.warn("【保存历史】会话ID或消息为空，跳过保存");
            return;
        }

        // 补充会话ID到消息DTO（确保历史与会话绑定）
        message.setSessionId(sessionId);
        log.info("【保存历史】sessionId={}, 消息内容：{}", sessionId, message);
        // 实际项目需对接数据库/Redis：如 historyMapper.insert(message);
    }


    // ------------------------------ 私有工具方法 ------------------------------

    /**
     * 获取或创建智能体（复用已有活跃实例，无则创建新实例）
     * 解决智能体重复创建问题，支持长对话会话复用
     */
    private MyAgent getOrCreateAgent(String sessionId) {
        return activeAgents.computeIfAbsent(sessionId, agentFactory::createMyAgent);
    }


    /**
     * 应用自定义参数到智能体（从请求中提取配置）
     */
    private void applyCustomParameters(AgentRequestVO request, MyAgent myAgent) {
        // 设置最大步骤数（需大于0才生效）
        if (request.getMaxSteps() != null && request.getMaxSteps() > 0) {
            myAgent.setMaxSteps(request.getMaxSteps());
            log.debug("【应用配置】会话ID：{}，设置最大步骤数：{}",
                    request.getSessionId(), request.getMaxSteps());
        }

        // 设置自定义系统提示（非空才生效）
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            myAgent.setSystemPrompt(request.getSystemPrompt());
            log.debug("【应用配置】会话ID：{}，设置自定义系统提示", request.getSessionId());
        }
    }


    /**
     * 保存高级任务执行历史（关联会话ID）
     */
    private void saveExecutionHistory(AgentRequestVO request, String result, String sessionId) {
        ChatMessageDTO messageDTO = new ChatMessageDTO()
                .setSessionId(sessionId)
                .setRole("assistant") // 角色：助手（智能体回复）
                .setContent(result)
                .setCreateTime(LocalDateTime.now())
                .setType("text");

        saveAgentHistory(sessionId, messageDTO);
    }


    /**
     * 绑定SSE发射器生命周期（完成/超时/错误时清理资源）
     */
    private void bindEmitterLifecycle(SseEmitter emitter, String sessionId) {
        // 连接完成：清理智能体与连接
        emitter.onCompletion(() -> {
            activeAgents.remove(sessionId);
            activeEmitters.remove(sessionId);
            log.debug("【SSE生命周期】会话完成，清理资源：{}", sessionId);
        });

        // 连接超时：清理资源
        emitter.onTimeout(() -> {
            activeAgents.remove(sessionId);
            activeEmitters.remove(sessionId);
            log.warn("【SSE生命周期】会话超时，清理资源：{}", sessionId);
        });

        // 连接错误：清理资源并打印日志
        emitter.onError(e -> {
            activeAgents.remove(sessionId);
            activeEmitters.remove(sessionId);
            log.error("【SSE生命周期】会话出错，清理资源：{}", sessionId, e);
        });
    }


    /**
     * 初始化失败时清理资源（避免内存泄漏）
     */
    private void cleanResourceOnError(String sessionId, SseEmitter emitter) {
        if (emitter != null) {
            emitter.complete();
        }
        activeAgents.remove(sessionId);
        activeEmitters.remove(sessionId);
        log.debug("【错误处理】会话初始化失败，清理资源：{}", sessionId);
    }

    // ------------------------------ 重载方法（兼容原有接口） ------------------------------
    @Override
    public AgentResponseVO getAgentStatus() {
        // 兼容原有无参方法，返回所有活跃会话汇总状态
        return getAgentStatus(null);
    }

    @Override
    public AgentResponseVO resetAgent() {
        // 兼容原有无参方法：无指定会话时，提示需传入会话ID
        return AgentResponseVO.warning("请指定会话ID（sessionId）以重置对应智能体");
    }
}