package com.ct.ai.agent.controller;

import com.ct.ai.agent.dto.DocumentUploadDTO;
import com.ct.ai.agent.service.DocumentService;
import com.ct.ai.agent.vo.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
@Tag(name = "文档管理接口", description = "提供RAG知识库文档上传、删除等功能")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "上传文档到RAG知识库", description = "支持Markdown格式，自动切片并存储到指定向量库")
    public BaseResponse<String> uploadDocument(
            @Parameter(description = "上传的文档文件（支持.md格式）", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "自定义文件名（不填则使用原文件名）")
            @RequestPart(value = "filename", required = false) String filename) {

        DocumentUploadDTO dto = new DocumentUploadDTO();
        dto.setFile(file);
        dto.setFilename(filename);

        String result = documentService.uploadToRag(dto);
        return BaseResponse.success(result);
    }
}