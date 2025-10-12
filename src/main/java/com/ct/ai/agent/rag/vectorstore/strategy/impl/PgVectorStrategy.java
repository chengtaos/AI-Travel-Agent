package com.ct.ai.agent.rag.vectorstore.strategy.impl;

import com.ct.ai.agent.rag.vectorstore.strategy.VectorStoreStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * PgVector向量库策略（具体策略）
 */
@Component // 注入Spring容器，供工厂调用
public class PgVectorStrategy implements VectorStoreStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    // 构造函数注入依赖（Spring自动装配）
    public PgVectorStrategy(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public VectorStore createVectorStore() {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536)                    // 向量维度：需与嵌入模型输出维度一致（Dashscope常见维度）
                .distanceType(COSINE_DISTANCE)       // 距离计算方式：余弦距离（适合文本相似度匹配）
                .indexType(HNSW)                     // 索引类型：HNSW（高效的近似最近邻搜索，适合大数据量）
                .initializeSchema(true)              // 自动初始化数据库表结构（首次启动时创建向量表）
                .schemaName("public")                // PostgreSQL schema名称：默认使用public schema
                .vectorTableName("vector_store")     // 向量存储表名：自定义表名，避免与其他表冲突
                .maxDocumentBatchSize(10000)         // 批量处理最大文档数：控制单次写入性能，避免数据库压力过大
                .build();
    }

    @Override
    public String getStrategyName() {
        return "pgvector"; // 策略标识，与配置文件对应
    }
}