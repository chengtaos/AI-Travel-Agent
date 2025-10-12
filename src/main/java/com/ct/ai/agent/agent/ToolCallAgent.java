package com.ct.ai.agent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工具调用智能体（继承ReAct模式）
 * 实现ReAct模式的"思考-行动"逻辑，专注于工具调用场景：
 * 1. 思考阶段：通过大模型判断是否需要调用工具、调用哪些工具
 * 2. 行动阶段：执行工具调用，处理返回结果，更新会话上下文
 */
@EqualsAndHashCode(callSuper = true) // 生成equals/hashCode时包含父类属性
@Data // 自动生成getter/setter、toString等方法
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 智能体可调用的工具列表（外部注入，如查询工具、计算工具等）
    private final ToolCallback[] availableTools;

    // 存储大模型返回的工具调用响应（思考阶段结果）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理器（Spring AI提供，用于执行具体工具调用逻辑）
    private final ToolCallingManager toolCallingManager;

    // 大模型聊天配置（禁用Spring AI内置工具调用，自主维护上下文）
    private final ChatOptions chatOptions;

    // 工具调用状态跟踪（避免连续失败导致死循环）
    private boolean lastToolCallSuccess = true; // 上一次工具调用是否成功
    private int consecutiveFailures = 0; // 连续失败次数
    private static final int MAX_CONSECUTIVE_FAILURES = 3; // 最大连续失败阈值


    /**
     * 构造函数：初始化工具列表、工具管理器、大模型配置
     *
     * @param availableTools 智能体可调用的工具数组
     */
    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build(); // 初始化工具调用管理器
        // 配置大模型：禁用内置工具执行，自主控制工具调用流程
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();

        log.info("[工具调用智能体: {}] 初始化完成，可用工具数量：{}",
                getName(), availableTools != null ? availableTools.length : 0);
    }


    /**
     * 思考阶段（实现ReAct抽象方法）
     * 核心逻辑：通过大模型分析会话上下文，判断是否需要调用工具
     *
     * @return true：需要调用工具；false：无需工具（直接返回结果）
     */
    @Override
    public boolean think() {
        // 1. 检查连续失败次数：超过阈值则终止执行，避免资源浪费
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            log.warn("[工具调用智能体: {}] 工具调用连续失败{}次，已达阈值，终止执行",
                    getName(), consecutiveFailures);
            setState(AgentState.ERROR);
            return false;
        }

        try {
            // 2. 追加下一步引导提示（如有），辅助大模型决策
            addNextStepPromptIfNeeded();

            // 3. 校验会话上下文：无消息则无法思考
            List<Message> messageList = getMessageList();
            if (messageList.isEmpty()) {
                log.warn("[工具调用智能体: {}] 会话上下文为空，无法执行思考逻辑", getName());
                return false;
            }

            // 4. 构建大模型请求：携带上下文和配置
            Prompt prompt = new Prompt(messageList, this.chatOptions);
            log.debug("[工具调用智能体: {}] 思考阶段 - 会话上下文消息数：{}", getName(), messageList.size());

            // 5. 调用大模型获取决策结果（是否需要调用工具）
            ChatResponse chatResponse = getChatClient()
                    .prompt(prompt) // 传入请求内容
                    .system(getSystemPrompt()) // 传入系统提示（定义智能体角色）
                    .toolCallbacks(availableTools) // 告知大模型可用工具
                    .call()
                    .chatResponse();

            // 6. 保存大模型响应，供后续行动阶段使用
            this.toolCallChatResponse = chatResponse;

            // 7. 解析大模型响应：提取助手消息和工具调用列表
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 8. 日志记录思考结果
            logThinkingResults(assistantMessage.getText(), toolCallList);

            // 9. 决策是否需要行动：无工具调用则直接返回结果，有则执行行动
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage); // 无工具调用，将助手消息加入上下文
                return false;
            }
            return true;

        } catch (Exception e) {
            // 思考阶段异常处理：记录错误，增加失败次数
            handleThinkingError(e);
            return false;
        }
    }


    /**
     * 行动阶段（实现ReAct抽象方法）
     * 核心逻辑：执行思考阶段决定的工具调用，处理返回结果，更新上下文
     *
     * @return 工具调用结果描述
     */
    @Override
    public String act() {
        // 1. 校验工具调用响应：无响应则无需执行
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            log.warn("[工具调用智能体: {}] 无待执行的工具调用", getName());
            return "无需执行工具调用";
        }

        try {
            log.debug("[工具调用智能体: {}] 行动阶段 - 开始执行工具调用", getName());

            // 2. 构建工具调用请求：携带会话上下文
            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
            // 3. 执行工具调用（通过工具管理器）
            ToolExecutionResult toolExecutionResult = toolCallingManager
                    .executeToolCalls(prompt, toolCallChatResponse);

            // 4. 更新会话上下文：将工具调用结果加入历史消息
            setMessageList(toolExecutionResult.conversationHistory());

            // 5. 提取最后一条工具响应消息（判断调用结果）
            Optional<ToolResponseMessage> toolResponseOpt = getLastToolResponseMessage(toolExecutionResult);
            if (toolResponseOpt.isEmpty()) {
                // 无工具响应：标记失败，增加失败次数
                lastToolCallSuccess = false;
                consecutiveFailures++;
                return "工具调用失败：未获取到工具响应";
            }

            ToolResponseMessage toolResponseMessage = toolResponseOpt.get();

            // 6. 检查是否调用"终止工具"：若是则标记任务完成
            if (isTerminateToolCalled(toolResponseMessage)) {
                setState(AgentState.FINISHED);
                log.info("[工具调用智能体: {}] 检测到终止工具调用，任务执行完成", getName());
            }

            // 7. 工具调用成功：重置失败计数器
            lastToolCallSuccess = true;
            consecutiveFailures = 0;

            // 8. 格式化工具调用结果（便于前端展示）
            String formattedResult = formatToolResults(toolResponseMessage);
            log.info("[工具调用智能体: {}] 工具调用结果：{}", getName(), formattedResult);

            return formattedResult;

        } catch (Exception e) {
            // 行动阶段异常处理：标记失败，增加失败次数
            lastToolCallSuccess = false;
            consecutiveFailures++;
            log.error("[工具调用智能体: {}] 工具调用执行异常", getName(), e);
            return "工具调用失败：" + e.getMessage();
        }
    }


    /**
     * 追加下一步引导提示（如有）到会话上下文
     * 用于辅助大模型理解当前步骤需执行的操作（如"请判断是否需要调用工具"）
     */
    private void addNextStepPromptIfNeeded() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
            log.debug("[工具调用智能体: {}] 已追加下一步引导提示到上下文", getName());
        }
    }


    /**
     * 记录思考阶段结果日志
     * 包括大模型的思考文本和待调用的工具列表
     */
    private void logThinkingResults(String thoughtText, List<AssistantMessage.ToolCall> toolCallList) {
        log.info("[工具调用智能体: {}] 思考结论：{}", getName(), thoughtText);
        log.info("[工具调用智能体: {}] 待调用工具数量：{}", getName(), toolCallList.size());

        // 打印工具调用详情（名称+参数）
        if (!toolCallList.isEmpty()) {
            String toolDetails = toolCallList.stream()
                    .map(toolCall -> String.format("工具名：%s，参数：%s",
                            toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.debug("[工具调用智能体: {}] 工具调用详情：\n{}", getName(), toolDetails);
        }
    }


    /**
     * 处理思考阶段异常
     * 记录错误日志，更新会话上下文（告知用户错误），增加失败次数
     */
    private void handleThinkingError(Exception e) {
        log.error("[工具调用智能体: {}] 思考阶段异常", getName(), e);
        // 将错误信息加入会话上下文，便于后续步骤参考
        getMessageList().add(new AssistantMessage("思考阶段遇到错误：" + e.getMessage()));
        lastToolCallSuccess = false;
        consecutiveFailures++;
    }


    /**
     * 从工具执行结果中提取最后一条工具响应消息
     * 工具执行结果中包含完整会话历史，最后一条通常是工具响应
     */
    private Optional<ToolResponseMessage> getLastToolResponseMessage(ToolExecutionResult toolExecutionResult) {
        if (toolExecutionResult == null ||
                toolExecutionResult.conversationHistory() == null ||
                toolExecutionResult.conversationHistory().isEmpty()) {
            return Optional.empty();
        }

        // 获取最后一条消息，判断是否为工具响应类型
        Message lastMessage = CollUtil.getLast(toolExecutionResult.conversationHistory());
        if (lastMessage instanceof ToolResponseMessage) {
            return Optional.of((ToolResponseMessage) lastMessage);
        }
        return Optional.empty();
    }


    /**
     * 检查是否调用了"终止工具"
     * 用于判断任务是否需要提前结束（如工具返回"无需继续执行"）
     */
    private boolean isTerminateToolCalled(ToolResponseMessage toolResponseMessage) {
        return toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name())); // "doTerminate"为终止工具名
    }


    /**
     * 格式化工具调用结果
     * 将工具响应转换为易读的字符串（工具名+返回数据）
     */
    private String formatToolResults(ToolResponseMessage toolResponseMessage) {
        return toolResponseMessage.getResponses().stream()
                .map(response -> String.format("工具「%s」返回结果：%s",
                        response.name(), response.responseData()))
                .collect(Collectors.joining("\n"));
    }


    /**
     * 获取不可修改的可用工具列表
     * 避免外部误修改工具列表，保证线程安全
     */
    public List<ToolCallback> getAvailableToolsList() {
        return availableTools == null ?
                Collections.emptyList() :
                List.of(availableTools);
    }


    /**
     * 资源清理
     * 重置工具调用相关状态，避免影响下一次任务执行
     */
    @Override
    protected void cleanup() {
        super.cleanup(); // 调用父类清理（如重置会话上下文、步骤计数）
        this.toolCallChatResponse = null; // 清空工具调用响应
        this.lastToolCallSuccess = true; // 重置成功标记
        this.consecutiveFailures = 0; // 重置连续失败次数
        log.debug("[工具调用智能体: {}] 资源清理完成", getName());
    }
}