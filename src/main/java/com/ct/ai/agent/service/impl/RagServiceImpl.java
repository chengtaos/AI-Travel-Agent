package com.ct.ai.agent.service.impl;

import com.ct.ai.agent.rag.retriever.QueryRewriter;
import com.ct.ai.agent.service.RagService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * RAG（检索增强生成）服务实现类
 * 核心逻辑：结合向量数据库检索和大模型生成，提升回答准确性（基于知识库内容）
 */
@Service
public class RagServiceImpl implements RagService {
    @Resource
    private ChatClient chatClient;

    // 查询改写器（优化用户输入，提升检索准确性）
    @Resource
    private QueryRewriter queryRewriter;

    // PgVector向量数据库（存储知识库向量，支持相似性检索）
    @Resource
    private VectorStore pgVectorVectorStore;


    /**
     * 执行RAG流式对话
     * 流程：用户输入→查询改写→知识库检索→大模型生成→流式返回
     *
     * @param message 用户输入的问题（如"AI智能体如何调用工具？"）
     * @param chatId  会话ID（用于维护多轮对话上下文，确保对话连贯性）
     * @return 流式回答结果（逐段返回，优化前端体验）
     */
    @Override
    public Flux<String> doChatWithRagQuery(String message, String chatId) {
        // 1. 改写用户查询：优化问题表述，提升后续检索精准度（如补全省略信息、修正歧义）
        String rewrittenQuery = queryRewriter.doQueryRewrite(message);

        // 2. 构建RAG流程：大模型+向量数据库检索
        return chatClient
                .prompt() // 构建对话请求
                .user(rewrittenQuery) // 传入改写后的用户查询
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId)) // 关联会话ID，启用对话记忆
                // 3. 应用RAG增强：从PgVector向量库检索相关知识
                .advisors(QuestionAnswerAdvisor.builder(pgVectorVectorStore)
                        .searchRequest(SearchRequest.builder()
                                .similarityThreshold(0.5) // 相似度阈值：只取相似度≥0.5的结果
                                .topK(3) // 取最相关的3条知识
                                .build())
                        .build())
                .stream() // 启用流式输出
                .content(); // 提取流式内容（每段回答文本）
    }
}