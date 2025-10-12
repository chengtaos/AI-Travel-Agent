package com.ct.ai.agent.rag.vectorstore.factory;

import com.ct.ai.agent.rag.vectorstore.strategy.VectorStoreStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量库工厂（策略工厂）
 * 根据配置动态选择并创建向量存储实例
 */
@Configuration
public class VectorStoreFactory {

    // 从配置文件读取当前使用的向量库策略（如application.yml中的vector-store.strategy=pgvector）
    @Value("${vector-store.strategy:pgvector}")
    private String defaultStrategy;

    // 注入所有VectorStoreStrategy实现（Spring自动收集@Component注解的策略）
    private final Map<String, VectorStoreStrategy> strategyMap = new ConcurrentHashMap<>();

    // 构造函数：初始化策略映射（key=策略名称，value=策略实例）
    public VectorStoreFactory(ApplicationContext applicationContext) {
        // 扫描并注册所有VectorStoreStrategy的实现类
        Map<String, VectorStoreStrategy> beans = applicationContext.getBeansOfType(VectorStoreStrategy.class);
        beans.forEach((beanName, strategy) -> {
            strategyMap.put(strategy.getStrategyName(), strategy);
        });
    }

    /**
     * 根据默认策略创建向量库实例（供外部调用）
     */
    @Bean(name = "vectorStore")
    public VectorStore createVectorStore() {
        return createVectorStore(defaultStrategy);
    }

    /**
     * 根据指定策略名称创建向量库实例（支持动态切换）
     */
    public VectorStore createVectorStore(String strategyName) {
        // 校验策略是否存在
        VectorStoreStrategy strategy = strategyMap.get(strategyName);
        Assert.notNull(strategy, "未找到向量存储策略：" + strategyName + "，请检查策略实现或配置");

        // 调用具体策略的创建方法
        return strategy.createVectorStore();
    }
}