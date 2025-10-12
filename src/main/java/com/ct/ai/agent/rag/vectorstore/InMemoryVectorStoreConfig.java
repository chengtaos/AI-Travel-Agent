package com.ct.ai.agent.rag.vectorstore;

import com.ct.ai.agent.rag.document.KeywordEnricher;
import com.ct.ai.agent.rag.document.MarkdownReader;
import com.ct.ai.agent.rag.document.SelfTokenTextSplitter;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 内存向量库（InMemoryVectorStore）配置类
 * 基于Spring AI的SimpleVectorStore实现，将文档向量存储在内存中
 * 特点：轻量、无外部依赖，适合测试环境或小规模知识库场景
 */
@Configuration // 标记为配置类，用于定义Spring Bean
public class InMemoryVectorStoreConfig {

    // 注入文档读取器：加载Markdown格式的原始知识库文档
    @Resource
    private MarkdownReader markdownReader;

    // 注入文档切片器：将长文档拆分为短文本片段（适配向量存储和检索）
    @Resource
    private SelfTokenTextSplitter selfTokenTextSplitter;

    // 注入关键词增强器：为文档补充关键词元信息，提升检索准确性
    @Resource
    private KeywordEnricher keywordEnricher;

    /**
     * 定义内存向量库Bean
     * 流程：加载Markdown文档 → 文档切片 → 关键词增强 → 生成向量并存储到内存
     *
     * @param dashscopeEmbeddingModel 嵌入模型（将文本转换为向量的核心组件）
     * @return 初始化完成的内存向量库（SimpleVectorStore）
     */
    @Bean(name = "inMemoryVectorStore")
    public VectorStore inMemoryVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        // 1. 初始化内存向量库：传入嵌入模型（用于文本→向量转换）
        SimpleVectorStore inMemoryVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        // 2. 加载原始Markdown文档（从classpath:document/目录读取）
        List<Document> originalDocs = markdownReader.loadMarkdowns();

        // 3. 文档切片：将长文档拆分为符合检索需求的短片段（此处使用默认切片规则，可替换为splitCustomized自定义规则）
        List<Document> splitDocs = selfTokenTextSplitter.splitDocuments(originalDocs);

        // 4. 关键词增强：为切片后的文档补充关键词元信息（如领域术语），提升后续检索相关性
        List<Document> enrichedDocs = keywordEnricher.enrichDocuments(splitDocs);

        // 5. 将增强后的文档写入内存向量库（内部自动完成文本→向量转换并存储）
        inMemoryVectorStore.add(enrichedDocs);

        return inMemoryVectorStore;
    }
}