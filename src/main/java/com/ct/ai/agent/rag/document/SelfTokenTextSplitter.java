package com.ct.ai.agent.rag.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档切片器（RAG知识库预处理组件）
 * 作用：将原始长文档拆分为符合向量数据库存储和检索需求的短文本片段（切片）
 * 核心目标：避免固定长度切分导致的语义断裂，确保切片包含完整信息单元，提升后续检索相关性
 */
@Component
public class SelfTokenTextSplitter {

    /**
     * 默认文档切片（使用TokenTextSplitter默认配置）
     * 适用于通用场景，无需自定义切分参数时使用
     *
     * @param documents 待切片的原始文档列表（如PDF解析后的长文本Document）
     * @return 切片后的文档列表（每个元素为独立的短文本片段）
     */
    public List<Document> splitDocuments(List<Document> documents) {
        // 初始化默认配置的TokenTextSplitter（按Token数切分，默认参数适配多数场景）
        TokenTextSplitter defaultSplitter = new TokenTextSplitter();
        // 执行切片：将输入文档列表拆分为短文本片段
        return defaultSplitter.apply(documents);
    }

    /**
     * 自定义规则文档切片（支持精细化参数配置）
     * 适用于特殊场景（如长文档需更小切片、需保留更多上下文重叠）
     *
     * @param documents 待切片的原始文档列表
     * @return 按自定义规则切片后的文档列表
     */
    public List<Document> splitCustomized(List<Document> documents) {
        // 初始化自定义配置的TokenTextSplitter，参数说明：
        // 1. chunkSize=200：每个切片最大Token数（控制切片长度）
        // 2. chunkOverlap=100：切片间重叠Token数（保留上下文关联，避免语义断裂）
        // 3. chunkNum=10：最大切片数量（单文档最多拆分为10个切片，防止过度拆分）
        // 4. maxChunkLength=5000：单文档最大处理Token数（过滤超长篇文档）
        // 5. trimWhitespace=true：自动去除切片前后空白字符（优化文本质量）
        TokenTextSplitter customizedSplitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        // 执行自定义规则切片
        return customizedSplitter.apply(documents);
    }
}