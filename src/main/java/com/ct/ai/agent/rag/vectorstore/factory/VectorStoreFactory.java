package com.ct.ai.agent.rag.vectorstore.factory;

import com.ct.ai.agent.rag.vectorstore.strategy.VectorStoreStrategy;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j  // 增加日志注解
public class VectorStoreFactory {

    // 从配置文件读取当前使用的向量库策略（默认pgvector）
    @Value("${vector-store.strategy:pgvector}")
    private String defaultStrategy;

    // 存储所有VectorStoreStrategy实现（线程安全）
    private final Map<String, VectorStoreStrategy> strategyMap = new ConcurrentHashMap<>();

    // 构造函数：初始化策略映射
    public VectorStoreFactory(ApplicationContext applicationContext) {
        // 扫描并注册所有VectorStoreStrategy的实现类
        Map<String, VectorStoreStrategy> beans = applicationContext.getBeansOfType(VectorStoreStrategy.class);

        // 增加日志：记录加载的策略数量
        log.info("检测到{}个向量存储策略实现", beans.size());

        beans.forEach((beanName, strategy) -> {
            String strategyName = strategy.getStrategyName();
            strategyMap.put(strategyName, strategy);
            log.info("注册向量存储策略：{}（bean名称：{}）", strategyName, beanName);
        });
    }

    /**
     * 根据默认策略创建向量库实例（供外部调用）
     * 重命名bean名称，避免与自动配置冲突
     */
    @Bean(name = "customVectorStore")  // 关键修改：bean名称改为customVectorStore
    public VectorStore createVectorStore() {
        log.info("使用默认策略创建向量库：{}", defaultStrategy);
        return createVectorStore(defaultStrategy);
    }

    /**
     * 根据指定策略名称创建向量库实例（支持动态切换）
     */
    public VectorStore createVectorStore(String strategyName) {
        // 校验策略名称非空
        Assert.hasText(strategyName, "策略名称不能为空");

        // 校验策略是否存在
        VectorStoreStrategy strategy = strategyMap.get(strategyName);
        Assert.notNull(strategy, "未找到向量存储策略：" + strategyName + "，已注册策略：" + strategyMap.keySet());

        // 调用具体策略的创建方法
        return strategy.createVectorStore();
    }
}