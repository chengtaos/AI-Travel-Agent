package com.ct.ai.agent.rag.vectorstore.strategy;

import org.springframework.ai.vectorstore.VectorStore;

/**
 * 向量存储策略接口（抽象策略）
 * 定义所有向量库的统一创建接口
 */
public interface VectorStoreStrategy {

    /**
     * 创建向量存储实例
     * @return 初始化完成的VectorStore
     */
    VectorStore createVectorStore();

    /**
     * 获取策略名称（用于工厂选择）
     * @return 策略名称（如"pgvector"、"in-memory"）
     */
    String getStrategyName();
}