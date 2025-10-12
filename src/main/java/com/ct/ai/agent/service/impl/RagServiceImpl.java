package com.ct.ai.agent.service.impl;

import com.ct.ai.agent.rag.advisor.factory.ContextualQueryAugmenterFactory;
import com.ct.ai.agent.rag.advisor.factory.RagAdvisorFactory;
import com.ct.ai.agent.rag.retriever.QueryRewriter;
import com.ct.ai.agent.service.RagService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * RAG（检索增强生成）服务实现类
 * 优化点：支持多检索源动态切换、统一空上下文处理、保留流式输出
 */
@Service
public class RagServiceImpl implements RagService {

    @Resource
    private ChatClient baseChatClient; // 基础ChatClient（不含默认RAG，来自ChatClientConfig）

    @Resource
    private QueryRewriter queryRewriter;

    // 注入RAG顾问工厂（用于动态创建不同检索源的RAG能力）
    @Resource
    private RagAdvisorFactory ragAdvisorFactory;

    @Resource(name = "ragQueryExecutor")
    private Executor ragQueryExecutor;

    // 从配置文件读取默认检索策略（可动态调整）
    @Value("${rag.default-strategy:local-pgvector}")
    private String defaultStrategy;

    /**
     * 执行RAG流式对话（支持指定检索策略）
     * 流程：用户输入→查询改写→动态选择检索源→知识库检索→大模型生成→流式返回
     */
    @Override
    public Flux<String> doChatWithRagQuery(String message, String sessionId, String strategyName) {
        // 1. 改写用户查询
        String rewrittenQuery = queryRewriter.doQueryRewrite(message);

        // 2. 确定检索策略
        String actualStrategy = (strategyName != null && !strategyName.isEmpty())
                ? strategyName
                : defaultStrategy;

        // 3. 创建空上下文处理器
        ContextualQueryAugmenter augmenter = new ContextualQueryAugmenterFactory.Builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(new PromptTemplate("抱歉，未找到相关知识库信息，请换个问题试试~"))
                .build();

        // 4. 获取RAG顾问
        Advisor ragAdvisor = ragAdvisorFactory.createRagAdvisor(actualStrategy, augmenter);

        // 5. 执行RAG流式对话，并绑定到专用线程池
        return Flux.defer(() -> baseChatClient  // 使用defer延迟执行，确保线程池生效
                .prompt()
                .user(rewrittenQuery)
                .advisors(spec -> spec.param(CONVERSATION_ID, sessionId))
                .advisors(ragAdvisor)
                .stream()
                .content()
        ).subscribeOn(Schedulers.fromExecutor(ragQueryExecutor));  // 绑定到ragQueryExecutor线程池
    }

    /**
     * 重载方法：使用默认检索策略（简化调用）
     */
    @Override
    public Flux<String> doChatWithRagQuery(String message, String chatId) {
        return doChatWithRagQuery(message, chatId, null);
    }
}