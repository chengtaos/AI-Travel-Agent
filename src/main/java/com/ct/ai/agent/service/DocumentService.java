package com.ct.ai.agent.service;

import com.ct.ai.agent.dto.DocumentUploadDTO;

public interface DocumentService {
    /**
     * 上传文档到RAG知识库
     *
     * @param dto 上传参数
     * @return 处理结果
     */
    String uploadToRag(DocumentUploadDTO dto);
}