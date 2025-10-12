package com.ct.ai.agent.rag.retriever;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询改写器（RAG检索前置处理组件）
 * 作用：优化用户原始查询，提升后续知识库检索的准确性
 * 核心逻辑：通过大模型对用户输入进行语义优化、补全或修正，生成更适合检索的查询语句
 */
@Component // 标记为Spring组件，供RAG服务调用
public class QueryRewriter {

    // 查询转换器：基于大模型实现查询改写逻辑
    private final QueryTransformer queryTransformer;

    /**
     * 构造函数：初始化查询改写器
     * 依赖大模型创建RewriteQueryTransformer（Spring AI提供的查询改写工具）
     *
     * @param dashscopeChatModel 大语言模型（用于理解并优化用户查询）
     */
    public QueryRewriter(ChatModel dashscopeChatModel) {
        // 构建大模型对话客户端
        ChatClient.Builder chatClientBuilder = ChatClient.builder(dashscopeChatModel);

        // 初始化查询重写转换器：通过大模型优化查询
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder) // 注入大模型客户端
                .build();
    }

    /**
     * 执行查询改写
     * 将用户原始输入转换为更精准、更符合检索需求的查询语句
     *
     * @param prompt 用户原始查询（如"AI咋调用工具？"）
     * @return 改写后的查询（如"人工智能智能体如何调用工具进行任务处理？"）
     */
    public String doQueryRewrite(String prompt) {
        // 1. 将原始查询包装为Query对象
        Query originalQuery = new Query(prompt);

        // 2. 调用转换器执行改写（内部通过大模型优化查询）
        Query transformedQuery = queryTransformer.transform(originalQuery);

        // 3. 返回改写后的查询文本
        return transformedQuery.text();
    }
}