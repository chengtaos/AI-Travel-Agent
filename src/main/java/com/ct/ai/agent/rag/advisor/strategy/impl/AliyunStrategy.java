package com.ct.ai.agent.rag.advisor.strategy.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import com.ct.ai.agent.rag.advisor.strategy.RagRetrievalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AliyunStrategy implements RagRetrievalStrategy {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    // 阿里云知识库索引（可改为配置注入，避免硬编码）
    @Value("${rag.aliyun.index-name:xxxx}")
    private String knowledgeIndex;

    @Override
    public DocumentRetriever createDocumentRetriever() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();

        log.info("初始化阿里云DashScope检索器，目标索引：{}", knowledgeIndex);
        return new DashScopeDocumentRetriever(
                dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(knowledgeIndex)
                        .build()
        );
    }

    @Override
    public String getStrategyName() {
        return "aliyun-dashscope"; // 策略标识
    }
}