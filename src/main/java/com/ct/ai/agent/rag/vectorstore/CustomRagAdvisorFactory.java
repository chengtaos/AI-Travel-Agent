package com.ct.ai.agent.rag.vectorstore;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.Assert;

/**
 * 自定义RAG检索增强顾问工厂类
 * 功能：标准化创建"向量存储+文档检索+空上下文处理"的完整RAG增强组件
 * 核心价值：封装检索器配置、过滤规则、空上下文处理逻辑，避免重复编码，支持按业务维度精准检索
 */
public class CustomRagAdvisorFactory {

    /**
     * 创建自定义RAG检索增强顾问（按文档文件名过滤）
     * 场景：需从指定文件名的文档中检索知识（如仅从"旅游指南.md"中获取答案）
     *
     * @param vectorStore 向量存储（如PgVector/SimpleVectorStore，提供检索数据源）
     * @param fileName    目标文档文件名（用于过滤检索范围，需与文档元数据中的"filename"字段匹配）
     * @return 配置完成的RAG检索增强顾问（可直接注入ChatClient使用）
     */
    public static Advisor createInstance(VectorStore vectorStore, String fileName) {
        // 1. 参数校验：避免空指针或无效参数导致检索异常
        Assert.notNull(vectorStore, "向量存储（VectorStore）不能为空，请传入有效的向量库实例");
        Assert.hasText(fileName, "文档文件名（fileName）不能为空或空白，请传入有效的文件名");

        // 2. 构建检索过滤规则：仅检索指定文件名的文档（缩小检索范围，提升准确性）
        Filter.Expression fileFilter = new FilterExpressionBuilder()
                .eq("filename", fileName) // 匹配元数据中"filename"字段等于目标文件名的文档
                .build();

        // 3. 创建文档检索器：封装向量存储、过滤规则、检索参数
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore) // 绑定向量存储（数据源）
                .filterExpression(fileFilter) // 应用文件名过滤
                .similarityThreshold(0.5) // 相似度阈值：仅保留相似度≥0.5的结果（过滤不相关文档）
                .topK(5) // 检索结果数量：返回Top5最相关的文档片段（平衡准确性和性能）
                .build();

        // 4. 构建RAG增强顾问：整合检索器 + 空上下文处理
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever) // 注入文档检索器（核心检索能力）
                .queryAugmenter(ContextualQueryAugmenterFactory.createInstance()) // 注入空上下文处理器（无结果时返回友好提示）
                .build();
    }

    /**
     * 重载方法：创建支持自定义检索参数的RAG顾问（灵活适配不同业务场景）
     * 场景：需自定义相似度阈值、检索结果数量，或扩展过滤规则时使用
     *
     * @param vectorStore         向量存储
     * @param fileName            目标文档文件名
     * @param similarityThreshold 相似度阈值（如0.6，值越高检索结果越精准）
     * @param topK                检索结果数量（如3，值越少性能越高，值越多覆盖度越广）
     * @return 自定义参数的RAG检索增强顾问
     */
    public static Advisor createInstance(
            VectorStore vectorStore,
            String fileName,
            double similarityThreshold,
            int topK) {
        // 参数校验：补充检索参数的合法性校验
        Assert.isTrue(similarityThreshold >= 0 && similarityThreshold <= 1,
                "相似度阈值（similarityThreshold）必须在0~1之间，请传入有效的值");
        Assert.isTrue(topK > 0 && topK <= 20,
                "检索结果数量（topK）必须在1~20之间，避免数量过多导致性能下降");

        // 复用基础逻辑，传入自定义参数
        Filter.Expression fileFilter = new FilterExpressionBuilder()
                .eq("filename", fileName)
                .build();

        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(fileFilter)
                .similarityThreshold(similarityThreshold) // 自定义相似度阈值
                .topK(topK) // 自定义检索结果数量
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenterFactory.createInstance())
                .build();
    }

    /**
     * 重载方法：创建支持自定义过滤规则的RAG顾问（高度灵活扩展）
     * 场景：需按多维度过滤（如文件名+文档类型），或复杂过滤逻辑时使用
     *
     * @param vectorStore      向量存储
     * @param filterExpression 自定义过滤规则（如多条件组合过滤）
     * @return 自定义过滤规则的RAG检索增强顾问
     */
    public static Advisor createInstance(VectorStore vectorStore, Filter.Expression filterExpression) {
        // 参数校验
        Assert.notNull(filterExpression, "过滤规则（filterExpression）不能为空，请传入有效的过滤条件");

        // 构建检索器时使用自定义过滤规则
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(filterExpression)
                .similarityThreshold(0.5)
                .topK(5)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}