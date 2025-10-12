package com.ct.ai.agent.rag.advisor.strategy.impl;

import com.ct.ai.agent.rag.advisor.strategy.RagRetrievalStrategy;
import jakarta.annotation.Resource;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalPgVectorStrategy implements RagRetrievalStrategy {
    @Resource(name = "vectorStore")
    private VectorStore vectorStore;

    // 检索参数
    private final double similarityThreshold;
    private final int topK;
    private final String targetFileName;

    public LocalPgVectorStrategy(
            @Value("${rag.local.similarity-threshold:0.5}") double similarityThreshold,
            @Value("${rag.local.topK:5}") int topK,
            @Value("${rag.local.target-filename:旅游指南.md}") String targetFileName) {
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
        this.targetFileName = targetFileName;
    }

    @Override
    public DocumentRetriever createDocumentRetriever() {
        // 1. 内部创建固定过滤规则：仅检索"filename"字段匹配targetFileName的文档
        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("filename", targetFileName) // 匹配文档元数据中的"filename"字段
                .build();

        // 2. 构建检索器
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(filter)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
    }

    @Override
    public String getStrategyName() {
        return "local-pgvector";
    }
}