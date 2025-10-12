package com.ct.ai.agent.service.impl;

import com.ct.ai.agent.dto.DocumentUploadDTO;
import com.ct.ai.agent.rag.document.KeywordEnricher;
import com.ct.ai.agent.rag.document.SelfTokenTextSplitter;
import com.ct.ai.agent.service.DocumentService;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Resource
    private SelfTokenTextSplitter textSplitter;

    @Resource
    private KeywordEnricher keywordEnricher;


    @Resource(name = "vectorStore")
    private VectorStore vectorStore;

    @Value("${rag.default-strategy:local-pgvector}")
    private String defaultStrategy;

    @Override
    public String uploadToRag(DocumentUploadDTO dto) {
        try {
            // 1. 校验文件
            MultipartFile file = dto.getFile();
            validateFile(file);

            // 2. 确定文件名
            String filename = StringUtils.hasText(dto.getFilename()) ? dto.getFilename() : file.getOriginalFilename();

            // 3. 解析文档（此处以Markdown为例，可扩展其他格式）
            List<Document> documents = parseDocument(file, filename);
            // 4. 文档切片
            List<Document> splitDocuments = textSplitter.splitCustomized(documents);

            // 5. 元信息增强（添加关键词）
            List<Document> enrichedDocuments = keywordEnricher.enrichDocuments(splitDocuments);

            // 6. 存储到向量库
            vectorStore.add(enrichedDocuments);

            return String.format("文档上传成功：%s，切片数量：%ds", filename, splitDocuments.size());
        } catch (Exception e) {
            return "文档上传失败：" + e.getMessage();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".md")) {
            throw new IllegalArgumentException("仅支持Markdown格式文件（.md）");
        }
    }

    private List<Document> parseDocument(MultipartFile file, String filename) throws IOException {
        // 此处简化处理，实际可根据文件类型调用不同的Reader
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        Document document = new Document(content);
        document.getMetadata().put("filename", filename);
        document.getMetadata().put("source", "upload");
        return List.of(document);
    }

}