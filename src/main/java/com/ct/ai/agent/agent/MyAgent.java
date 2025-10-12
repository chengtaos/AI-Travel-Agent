package com.ct.ai.agent.agent;

import com.ct.ai.agent.agent.property.ToolCallProperties;
import com.ct.ai.agent.llm.advisor.LoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 继承工具调用智能体（ToolCallAgent），具备自主任务规划、工具自动选择、复杂问题拆解能力
 * 通过整合多类工具，自动匹配最优解决方案，支持多步骤交互处理用户复杂需求
 */
@Component
@Slf4j
public class MyAgent extends ToolCallAgent {

    /**
     * 系统提示词：定义MyAgent的角色定位、核心能力和行为准则
     * 作用：告知大模型"你是谁、能做什么、要遵循什么规则"，确保智能体行为符合预期
     */
    private static final String SYSTEM_PROMPT = """
            你是MyAgent，一款全能AI助手，核心目标是解决用户提出的各类任务需求。
            你可调用多种工具，通过工具组合高效完成复杂请求。
            
            核心能力：
            1. 深度理解用户需求，将复杂任务拆解为可执行的步骤
            2. 针对每个任务环节，自动选择最适配的工具（或工具组合）
            3. 清晰解释你的操作逻辑和结果，让用户理解任务进展
            4. 在多步骤交互中，持续维护会话上下文，确保任务连贯性
            
            行为准则：始终以"准确性"和"实用性"为首要原则，优先提供能切实解决问题的响应。
            """;

    /**
     * 下一步提示词：规范智能体处理多步骤任务的决策流程
     * 作用：指导大模型在每一步如何分析状态、选择工具、推进任务，避免决策混乱
     */
    private static final String NEXT_STEP_PROMPT = """
            根据用户需求和历史对话记录，请按以下逻辑处理：
            
            1. 先分析当前任务的进展状态（已完成什么、还需做什么）
            2. 主动选择最适配的工具（或工具组合），避免无效调用
            3. 若任务复杂，先拆分为更小的可执行步骤，逐步推进
            4. 每调用一次工具后，清晰说明结果，并提示下一步计划
            5. 若任务已完成或需终止交互，调用「doTerminate」工具结束流程
            
            核心目标：找到最高效的任务解决路径，减少不必要的步骤。
            """;


    /**
     * 构造函数：初始化MyAgent的核心配置
     * 注入依赖（可用工具、大模型），设置智能体基础属性
     *
     * @param allTools           所有可用工具（通过@Qualifier指定注入"allTools"标识的工具数组）
     * @param dashscopeChatModel 大语言模型（如DashScope提供的LLM，智能体的"大脑"）
     */
    public MyAgent(@Qualifier("allTools") ToolCallback[] allTools,
                   ChatModel dashscopeChatModel,
                   ToolCallProperties toolCallProperties) {
        super(allTools, toolCallProperties); // 调用父类构造函数，传入可用工具列表

        try {
            // 1. 设置智能体基础属性
            this.setName("myAgent"); // 智能体名称（用于日志标识、会话区分）
            this.setSystemPrompt(SYSTEM_PROMPT); // 注入系统提示词
            this.setNextStepPrompt(NEXT_STEP_PROMPT); // 注入下一步引导提示词
            this.setMaxSteps(20); // 最大执行步骤（避免因逻辑异常导致无限循环）

            log.info("初始化MyAgent智能体，可用工具数量：{}",
                    allTools != null ? allTools.length : 0);

            // 2. 初始化大模型对话客户端（智能体与大模型的交互入口）
            ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                    .defaultAdvisors(new LoggerAdvisor())
                    .build();
            this.setChatClient(chatClient);
            log.info("MyAgent智能体初始化完成，已具备任务处理能力");
        } catch (Exception e) {
            log.error("MyAgent智能体初始化失败", e);
            throw new RuntimeException("MyAgent智能体初始化失败：" + e.getMessage(), e);
        }
    }


    /**
     * 重置智能体状态（重写父类方法）
     * 作用：清空上一次任务的残留数据（会话历史、步骤计数等），为新任务做准备
     */
    @Override
    public void reset() {
        super.reset(); // 调用父类重置方法：恢复状态为IDLE、清空会话历史、重置步骤计数
        // 可扩展MyAgent专属重置逻辑（如清空工具调用缓存、恢复默认配置等）
        log.debug("MyAgent智能体重置完成，已准备好接收新任务");
    }
}