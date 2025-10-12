package com.ct.ai.agent.rag.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Markdown文档读取器（RAG知识库导入组件）
 * 作用：加载并解析项目中指定路径的Markdown文档，转换为AI处理的Document格式
 * 应用场景：知识库初始化时批量导入.md格式文档，为RAG检索提供数据源
 */
@Component
public class MarkdownReader {

    // 注入classpath:document/目录下的所有.md资源（通过@Value指定路径和文件类型）
    private final Resource[] markdownResources;

    /**
     * 构造函数：初始化Markdown资源
     * @param resources 自动注入的.md文件资源数组（路径：classpath:document/*.md）
     */
    public MarkdownReader(@Value("classpath:document/*.md") Resource[] resources) {
        this.markdownResources = resources;
    }

    /**
     * 批量加载并解析Markdown文档
     * 将指定目录下的所有.md文件转换为Document对象（包含文本内容和元数据）
     *
     * @return 解析后的Document列表（每个元素对应一个Markdown文档的内容）
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>(); // 存储所有解析后的文档

        // 遍历所有注入的Markdown资源，逐个解析
        for (Resource resource : markdownResources) {
            // 1. 配置Markdown读取规则
            MarkdownDocumentReaderConfig readerConfig = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true) // 按水平线分割为多个文档片段
                    .withIncludeCodeBlock(false) // 不包含代码块（根据业务需求调整）
                    .withIncludeBlockquote(false) // 不包含引用块（根据业务需求调整）
                    .withAdditionalMetadata("filename", Objects.requireNonNull(resource.getFilename())) // 添加文件名元数据（便于溯源）
                    .build();

            // 2. 创建Markdown文档读取器，传入资源和配置
            MarkdownDocumentReader documentReader = new MarkdownDocumentReader(resource, readerConfig);

            // 3. 读取并解析文档，添加到结果列表
            allDocuments.addAll(documentReader.read());
        }

        return allDocuments;
    }
}