package com.ct.ai.agent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 智能体抽象基类
 * 封装智能体通用核心能力：状态管理、会话记忆、同步/流式执行流程、参数校验
 * 子类需实现step()方法，定义具体业务的单步执行逻辑（如工具调用、大模型交互）
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 智能体基础标识
    private String name;

    // 提示词配置
    private String systemPrompt; // 系统提示（定义智能体角色/规则）
    private String nextStepPrompt; // 下一步引导提示（用于多步骤执行时的逻辑衔接）

    // 智能体状态管理（初始为空闲态）
    private AgentState state = AgentState.IDLE;

    // 执行流程控制
    private int currentStep = 0; // 当前执行步骤
    private int maxSteps = 10; // 最大执行步骤（避免无限循环）

    // 大模型依赖（核心交互组件，需外部注入）
    private ChatClient chatClient;

    // 会话记忆（维护历史消息上下文，支持多轮对话）
    private List<Message> messageList = new ArrayList<>();


    /**
     * 同步执行智能体任务
     * 按步骤循环执行，直到任务完成或达到最大步骤，返回完整结果
     *
     * @param userPrompt 用户输入提示词
     * @return 所有步骤的执行结果（按步骤拼接）
     * @throws IllegalArgumentException 参数/状态非法（如提示词为空、非空闲态执行）
     */
    public String run(String userPrompt) {
        // 执行前校验：确保参数合法、状态正确
        validateBeforeRun(userPrompt);

        // 初始化：设置运行态、记录用户消息到上下文
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> stepResults = new ArrayList<>(); // 存储每一步执行结果
        try {
            // 步骤循环：未完成且未超最大步骤时持续执行
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1; // 步骤从1开始计数
                log.info("[智能体: {}] 执行步骤 {}/{}", name, currentStep, maxSteps);

                // 调用子类实现的单步逻辑，获取步骤结果
                String stepResult = step();
                stepResults.add("步骤 " + currentStep + ": " + stepResult);

                // 短暂休眠：避免高频请求触发大模型速率限制
                TimeUnit.MILLISECONDS.sleep(100);
            }

            // 处理"超最大步骤"场景
            if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                String overStepMsg = "执行终止：已达到最大步骤（" + maxSteps + "）";
                stepResults.add(overStepMsg);
                log.warn("[智能体: {}] {}", name, overStepMsg);
            }
            return String.join("\n", stepResults); // 拼接所有步骤结果返回
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("[智能体: {}] 执行异常", name, e);
            return "执行错误: " + e.getMessage();
        } finally {
            cleanup();
        }
    }


    /**
     * 流式执行智能体任务
     * 异步按步骤执行，每步结果通过SSE实时推送给前端，避免前端长时间等待
     *
     * @param userPrompt 用户输入提示词
     * @return SseEmitter：SSE连接发射器（前端通过该对象接收流式数据）
     */
    public SseEmitter runStream(String userPrompt) {
        //设置5分钟超时
        SseEmitter sseEmitter = new SseEmitter(300000L);

        // 异步执行：避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                // 执行前校验
                validateBeforeRun(userPrompt);
                // 初始化：设置运行态、记录用户消息
                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                // 发送启动通知：告知前端智能体开始执行
                sseEmitter.send("智能体 '" + name + "' 开始执行...");
                // 步骤循环：流式推送每步结果
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("[智能体: {}] 流式执行步骤 {}/{}", name, currentStep, maxSteps);
                    // 单步执行 + 流式推送结果
                    String stepResult = step();
                    sseEmitter.send("步骤 " + currentStep + ": " + stepResult);
                    // 短暂休眠：避免速率限制
                    TimeUnit.MILLISECONDS.sleep(100);
                }
                // 处理"超最大步骤"场景
                if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                    state = AgentState.FINISHED;
                    String overStepMsg = "执行终止：已达到最大步骤（" + maxSteps + "）";
                    log.warn("[智能体: {}] {}", name, overStepMsg);
                    sseEmitter.send(overStepMsg);
                }
                // 发送完成通知，标记SSE连接正常完成
                sseEmitter.send("智能体 '" + name + "' 执行完成");
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                String errorMsg = "执行错误：" + e.getMessage();
                log.error("[智能体: {}] {}", name, errorMsg, e);
                try {
                    sseEmitter.send(errorMsg);
                    sseEmitter.complete();
                } catch (IOException ex) {
                    log.error("[智能体: {}] SSE推送错误消息失败", name, ex);
                    sseEmitter.completeWithError(ex); // 标记连接错误完成
                }
            } finally {
                cleanup();
            }
        });
        // 配置SSE连接的生命周期回调（超时、完成、错误）
        configureEmitterCallbacks(sseEmitter);
        return sseEmitter;
    }


    /**
     * 配置SSE发射器的生命周期回调
     * 处理连接超时、正常完成、异常错误场景，确保状态正确更新和资源清理
     */
    private void configureEmitterCallbacks(SseEmitter sseEmitter) {
        // 超时回调：连接超时后标记错误态，清理资源
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            log.warn("[智能体: {}] SSE连接超时", name);
            cleanup();
        });
        // 完成回调：连接正常完成后更新状态（若仍为运行态则标记为完成）
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            log.info("[智能体: {}] SSE连接已完成", name);
            cleanup();
        });
        // 错误回调：连接异常时标记错误态，清理资源
        sseEmitter.onError((ex) -> {
            this.state = AgentState.ERROR;
            log.error("[智能体: {}] SSE连接异常", name, ex);
            cleanup();
        });
    }


    /**
     * 执行前校验：确保参数合法、状态正确、依赖就绪
     * 校验不通过则抛出异常，提前阻断非法执行
     */
    private void validateBeforeRun(String userPrompt) {
        // 1. 状态校验：仅允许从"空闲态"执行
        if (this.state != AgentState.IDLE) {
            String error = "无法从状态 [" + this.state + "] 执行智能体";
            log.error("[智能体: {}] {}", name, error);
            throw new IllegalArgumentException(error);
        }
        // 2. 参数校验：用户提示词不能为空
        if (StrUtil.isBlank(userPrompt)) {
            String error = "用户提示词不能为空";
            log.error("[智能体: {}] {}", name, error);
            throw new IllegalArgumentException(error);
        }
        // 3. 依赖校验：大模型客户端（ChatClient）必须已初始化
        if (chatClient == null) {
            String error = "大模型客户端（ChatClient）未初始化";
            log.error("[智能体: {}] {}", name, error);
            throw new IllegalStateException(error);
        }
    }


    /**
     * 抽象方法：单步执行逻辑
     * 由子类实现，定义具体业务的每一步操作（如调用工具、请求大模型、处理结果）
     *
     * @return 单步执行结果
     */
    public abstract String step();

    /**
     * 获取会话历史（不可修改）
     * 对外提供只读的消息列表，避免外部误修改上下文，保证线程安全
     *
     * @return 不可修改的会话消息列表
     */
    public List<Message> getMessageHistory() {
        return Collections.unmodifiableList(messageList);
    }

    /**
     * 重置智能体状态
     * 恢复到初始空闲态，清空步骤计数和会话历史，为下一次执行做准备
     */
    public void reset() {
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        this.messageList.clear();
        log.info("[智能体: {}] 已重置为空闲态", name);
    }

    /**
     * 资源清理
     * 基类提供基础清理逻辑，子类可重写扩展（如关闭工具连接、释放临时资源）
     */
    protected void cleanup() {
        log.debug("[智能体: {}] 执行资源清理", name);
    }
}