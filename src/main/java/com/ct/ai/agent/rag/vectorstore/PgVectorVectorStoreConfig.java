package com.ct.ai.agent.rag.vectorstore;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * PgVector向量库配置类
 * 基于PostgreSQL的PgVector扩展实现向量存储，适用于生产环境（持久化、高查询性能）
 * 解决多EmbeddingModel存在时的注入冲突问题，通过显式配置指定依赖的模型和参数
 */
@Configuration
public class PgVectorVectorStoreConfig {

    /**
     * 定义PgVector向量库Bean
     * 显式配置向量存储参数，避免多模型注入冲突，适配生产环境需求
     *
     * @param jdbcTemplate            PostgreSQL数据库连接模板（用于操作数据库表）
     * @param dashscopeEmbeddingModel 嵌入模型（指定使用Dashscope的模型，将文本转换为向量）
     * @return 初始化完成的PgVector向量库
     */
    @Bean(name = "pgVectorVectorStore")
    public VectorStore pgVectorVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel dashscopeEmbeddingModel) {

        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // 向量维度：需与嵌入模型输出维度一致（Dashscope常见维度）
                .distanceType(COSINE_DISTANCE)       // 距离计算方式：余弦距离（适合文本相似度匹配）
                .indexType(HNSW)                     // 索引类型：HNSW（高效的近似最近邻搜索，适合大数据量）
                .initializeSchema(true)              // 自动初始化数据库表结构（首次启动时创建向量表）
                .schemaName("public")                // PostgreSQL schema名称：默认使用public schema
                .vectorTableName("vector_store")     // 向量存储表名：自定义表名，避免与其他表冲突
                .maxDocumentBatchSize(10000)         // 批量处理最大文档数：控制单次写入性能，避免数据库压力过大
                .build();
    }
}