package com.ct.ai.agent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct模式智能体抽象类（继承自基础智能体）
 * 实现"思考-行动"（Reasoning and Acting）循环逻辑，是智能体决策执行的核心模式
 * 核心流程：先分析当前状态（思考）→ 决定是否需要行动 → 执行行动（如需）
 */
@EqualsAndHashCode(callSuper = true) // 生成equals和hashCode时包含父类属性
@Data // 自动生成getter/setter等方法
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考阶段：分析当前状态和目标，决定是否需要执行行动
     * 由子类实现具体逻辑（如基于上下文判断是否调用工具、是否直接回答）
     *
     * @return true：需要执行行动；false：无需行动（可直接返回结果）
     */
    public abstract boolean think();

    /**
     * 行动阶段：执行思考阶段决定的具体操作
     * 由子类实现具体行动（如调用工具、查询数据、生成回答等）
     *
     * @return 行动执行结果（字符串描述）
     */
    public abstract String act();

    /**
     * 单步执行逻辑（实现父类抽象方法）
     * 封装ReAct模式核心流程：思考 → 决策 → 行动（按需）
     *
     * @return 本步骤执行结果
     */
    @Override
    public String step() {
        try {
            log.debug("[ReAct智能体: {}] 开始步骤执行（思考阶段）", getName());

            // 1. 思考阶段：判断是否需要行动
            boolean needAction = think();

            // 2. 无需行动：直接返回思考结论
            if (!needAction) {
                log.debug("[ReAct智能体: {}] 思考完成，无需行动", getName());
                return "思考完成：无需额外操作，可直接返回结果";
            }

            // 3. 需要行动：执行具体操作并返回结果
            log.debug("[ReAct智能体: {}] 思考完成，开始执行行动", getName());
            String actionResult = act();
            log.debug("[ReAct智能体: {}] 行动执行完成，结果：{}", getName(), actionResult);

            return "行动执行结果：" + actionResult;

        } catch (Exception e) {
            log.error("[ReAct智能体: {}] 步骤执行异常", getName(), e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 重置智能体状态（重写父类方法）
     * 在父类重置基础状态（空闲态、步骤计数、消息历史）的基础上，可扩展ReAct模式特定重置逻辑
     */
    @Override
    public void reset() {
        super.reset(); // 调用父类重置方法，恢复基础状态
        log.debug("[ReAct智能体: {}] 已完成重置（包含ReAct模式特定状态）", getName());
    }
}