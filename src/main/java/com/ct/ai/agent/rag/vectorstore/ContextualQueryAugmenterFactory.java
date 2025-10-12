package com.ct.ai.agent.rag.vectorstore;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * ContextualQueryAugmenter工厂类
 * 功能：统一创建上下文查询增强器实例，标准化空上下文处理逻辑
 * 核心价值：避免重复编码，支持统一配置空上下文的响应模板和处理规则
 */
public class ContextualQueryAugmenterFactory {

    /**
     * 创建上下文查询增强器实例（默认配置：禁用空上下文，自定义空响应模板）
     * 场景：当RAG检索无匹配结果（空上下文）时，返回标准化的友好提示，而非默认内容
     *
     * @return 配置完成的ContextualQueryAugmenter实例
     */
    public static ContextualQueryAugmenter createInstance() {
        // 1. 定义空上下文响应模板：标准化提示内容，包含业务专属指引（如人工客服联系方式）
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                抱歉，我目前仅能回答与【xx业务领域】相关的问题，暂无法为您提供当前查询的解答。
                若有进一步需求，可联系人工客服获取帮助，联系方式：11111111111
                """);

        // 2. 构建并配置上下文查询增强器
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false) // 禁用空上下文：检索无结果时，不允许使用空上下文生成回答
                .emptyContextPromptTemplate(emptyContextPromptTemplate) // 注入空上下文时的响应模板
                .build();
    }

    /**
     * 重载方法：创建支持自定义空响应模板的上下文查询增强器
     * 场景：不同业务模块需差异化空上下文提示时，支持传入自定义模板
     *
     * @param emptyContextPromptTemplate 自定义空上下文响应模板
     * @return 自定义配置的ContextualQueryAugmenter实例
     */
    public static ContextualQueryAugmenter createInstance(PromptTemplate emptyContextPromptTemplate) {
        // 校验模板非空，避免空指针异常
        if (emptyContextPromptTemplate == null) {
            throw new IllegalArgumentException("空上下文响应模板不能为空，请传入有效的PromptTemplate");
        }

        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }

    /**
     * 重载方法：创建支持灵活配置的上下文查询增强器
     * 场景：需动态控制是否允许空上下文，或自定义空响应模板时使用
     *
     * @param allowEmptyContext 是否允许空上下文（true：允许用空上下文生成回答；false：禁用）
     * @param emptyContextPromptTemplate 空上下文响应模板（allowEmptyContext=false时生效）
     * @return 全量配置的ContextualQueryAugmenter实例
     */
    public static ContextualQueryAugmenter createInstance(boolean allowEmptyContext,
                                                          PromptTemplate emptyContextPromptTemplate) {
        if (!allowEmptyContext && emptyContextPromptTemplate == null) {
            throw new IllegalArgumentException("禁用空上下文时，必须传入有效的空响应模板");
        }

        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(allowEmptyContext)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}