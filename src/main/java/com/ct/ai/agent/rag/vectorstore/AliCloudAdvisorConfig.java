package com.ct.ai.agent.rag.vectorstore;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云DashScope知识库RAG增强顾问配置类
 * 作用：集成阿里云DashScope云知识库服务，提供基于云存储的检索增强能力
 * 应用场景：无需本地维护向量库，直接调用阿里云托管知识库进行RAG检索
 */
@Configuration // 标记为Spring配置类，定义云知识库相关Bean
@Slf4j
public class AliCloudAdvisorConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    /**
     * 定义阿里云RAG advisor
     * 核心逻辑：创建DashScope知识库检索器，封装为Spring AI的Advisor，供ChatClient调用
     *
     * @return 阿里云RAG增强顾问（Advisor）
     */
    @Bean(name = "ragCloudAdvisor")
    public Advisor ragCloudAdvisor() {
        // 1. 初始化阿里云DashScope API客户端（用于访问云知识库服务）
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey) // 传入API密钥，鉴权访问
                .build();

        // 2. 配置阿里云知识库索引名称
        final String KNOWLEDGE_INDEX = "xxxx";
        log.info("初始化阿里云DashScope RAG顾问，目标知识库索引：{}", KNOWLEDGE_INDEX);

        // 3. 创建云文档检索器（从阿里云知识库中检索相关文档）
        DocumentRetriever cloudDocumentRetriever = new DashScopeDocumentRetriever(
                dashScopeApi, // DashScope API客户端
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(KNOWLEDGE_INDEX) // 指定目标知识库索引
                        .build() // 构建检索器配置
        );

        // 4. 封装为RAG增强顾问（适配Spring AI的Advisor接口，供ChatClient调用）
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(cloudDocumentRetriever) // 注入云检索器
                .build();
    }
}