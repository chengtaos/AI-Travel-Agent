package com.ct.ai.agent.rag.vectorstore.strategy.impl;

import com.ct.ai.agent.rag.document.KeywordEnricher;
import com.ct.ai.agent.rag.document.MarkdownReader;
import com.ct.ai.agent.rag.document.SelfTokenTextSplitter;
import com.ct.ai.agent.rag.vectorstore.strategy.VectorStoreStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内存向量库策略（具体策略）
 */
@Component // 注入Spring容器，供工厂调用
public class InMemoryVectorStrategy implements VectorStoreStrategy {

    private final EmbeddingModel embeddingModel;
    private final MarkdownReader markdownReader;
    private final SelfTokenTextSplitter textSplitter;
    private final KeywordEnricher keywordEnricher;

    // 构造函数注入依赖（Spring自动装配）
    public InMemoryVectorStrategy(EmbeddingModel embeddingModel,
                                  MarkdownReader markdownReader,
                                  SelfTokenTextSplitter textSplitter,
                                  KeywordEnricher keywordEnricher) {
        this.embeddingModel = embeddingModel;
        this.markdownReader = markdownReader;
        this.textSplitter = textSplitter;
        this.keywordEnricher = keywordEnricher;
    }

    @Override
    public VectorStore createVectorStore() {
        // 1. 初始化内存向量库：传入嵌入模型（用于文本→向量转换）
        SimpleVectorStore inMemoryVectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // 2. 加载原始Markdown文档（从classpath:document/目录读取）
        List<Document> originalDocs = markdownReader.loadMarkdowns();

        // 3. 文档切片：将长文档拆分为符合检索需求的短片段（此处使用默认切片规则，可替换为splitCustomized自定义规则）
        List<Document> splitDocs = textSplitter.splitDocuments(originalDocs);
//        List<Document> splitDocs = selfTokenTextSplitter.splitCustomized(originalDocs);

        // 4. 关键词增强：为切片后的文档补充关键词元信息（如领域术语），提升后续检索相关性
        List<Document> enrichedDocs = keywordEnricher.enrichDocuments(splitDocs);

        // 5. 将增强后的文档写入内存向量库（内部自动完成文本→向量转换并存储）
        inMemoryVectorStore.add(enrichedDocs);

        return inMemoryVectorStore;
    }

    @Override
    public String getStrategyName() {
        return "in-memory"; // 策略标识，与配置文件对应
    }
}