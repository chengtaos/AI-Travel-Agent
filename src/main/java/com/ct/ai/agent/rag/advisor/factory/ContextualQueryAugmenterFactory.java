package com.ct.ai.agent.rag.advisor.factory;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.util.Assert;

/**
 * 空上下文处理器工厂
 */
public class ContextualQueryAugmenterFactory {

    private ContextualQueryAugmenterFactory() {
    }

    /**
     * 创建默认空上下文处理器（禁用空上下文，默认提示模板）
     */
    public static ContextualQueryAugmenter createDefault() {
        return new Builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(new PromptTemplate("""
                        抱歉，仅支持【xx业务领域】相关问题，暂无法解答当前查询。
                        如需帮助，可联系人工客服：11111111111
                        """))
                .build();
    }

    /**
     * 建造者类：简化空上下文处理器的配置
     */
    public static class Builder {
        private boolean allowEmptyContext = false; // 默认禁用空上下文
        private PromptTemplate emptyContextPromptTemplate; // 空上下文提示模板

        /**
         * 是否允许空上下文（true：允许用空上下文生成回答；false：禁用）
         */
        public Builder allowEmptyContext(boolean allowEmptyContext) {
            this.allowEmptyContext = allowEmptyContext;
            return this;
        }

        /**
         * 设置空上下文提示模板（禁用空上下文时必传）
         */
        public Builder emptyContextPromptTemplate(PromptTemplate template) {
            this.emptyContextPromptTemplate = template;
            return this;
        }

        /**
         * 构建空上下文处理器（校验参数合法性）
         */
        public ContextualQueryAugmenter build() {
            // 禁用空上下文时，必须传入提示模板
            if (!allowEmptyContext) {
                Assert.notNull(emptyContextPromptTemplate, "禁用空上下文时，必须设置提示模板");
            }
            return ContextualQueryAugmenter.builder()
                    .allowEmptyContext(allowEmptyContext)
                    .emptyContextPromptTemplate(emptyContextPromptTemplate)
                    .build();
        }
    }
}