package com.ct.ai.agent.rag.advisor.strategy;

import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

/**
 * RAG检索策略接口（抽象策略）
 * 定义不同检索源（阿里云/本地向量库）的统一检索器创建规范
 */
public interface RagRetrievalStrategy {

    /**
     * 创建文档检索器（不同策略实现不同检索逻辑）
     *
     * @return 适配当前策略的DocumentRetriever
     */
    DocumentRetriever createDocumentRetriever();

    /**
     * 获取策略名称（用于工厂选择策略）
     *
     * @return 策略标识（如"aliyun-dashscope"、"local-pgvector"）
     */
    String getStrategyName();
}