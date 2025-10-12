package com.ct.ai.agent.rag.advisor.factory;

import com.ct.ai.agent.rag.advisor.strategy.RagRetrievalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG增强顾问工厂（统一创建入口）
 * 功能：根据配置选择检索策略，自动注入空上下文处理器，生成RAG Advisor
 */
@Configuration
@Slf4j
public class RagAdvisorFactory {

    // 从配置文件读取默认检索策略（如"aliyun-dashscope"或"local-pgvector"）
    @Value("${rag.default-strategy:local-pgvector}")
    private String defaultStrategy;

    // 存储所有检索策略（Spring自动收集@Component注解的策略）
    private final Map<String, RagRetrievalStrategy> strategyMap = new ConcurrentHashMap<>();

    // 构造函数：初始化策略映射
    public RagAdvisorFactory(ApplicationContext applicationContext) {
        Map<String, RagRetrievalStrategy> beans = applicationContext.getBeansOfType(RagRetrievalStrategy.class);
        beans.forEach((beanName, strategy) -> {
            strategyMap.put(strategy.getStrategyName(), strategy);
            log.info("加载RAG检索策略：{} -> {}", strategy.getStrategyName(), strategy.getClass().getSimpleName());
        });
    }

    /**
     * 创建默认RAG顾问（按配置的默认策略）
     * 自动注入空上下文处理器（兜底逻辑）
     */
    @Bean(name = "defaultRagAdvisor")
    public Advisor createDefaultRagAdvisor() {
        // 自定义空上下文处理器
        ContextualQueryAugmenter customAugmenter = new ContextualQueryAugmenterFactory.Builder()
                .allowEmptyContext(false) // 禁用空上下文
                .emptyContextPromptTemplate(new PromptTemplate("""
                抱歉，仅支持【旅游咨询】相关问题，暂无法解答当前查询。
                如需帮助，可联系金融客服：95555
                """)) // 自定义提示内容
                .build();
        return createRagAdvisor(defaultStrategy, customAugmenter);
    }

    /**
     * 自定义创建RAG顾问（支持指定策略和空上下文处理器）
     */
    public Advisor createRagAdvisor(String strategyName, ContextualQueryAugmenter queryAugmenter) {
        // 1. 校验策略是否存在
        RagRetrievalStrategy strategy = strategyMap.get(strategyName);
        Assert.notNull(strategy, "未找到RAG检索策略：" + strategyName + "，请检查策略实现或配置");

        // 2. 校验空上下文处理器（避免遗漏兜底逻辑）
        Assert.notNull(queryAugmenter, "空上下文处理器不能为空，请传入有效的ContextualQueryAugmenter");

        // 3. 创建检索器 + 构建RAG顾问
        DocumentRetriever retriever = strategy.createDocumentRetriever();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(queryAugmenter) // 强制注入兜底组件
                .build();
    }
}