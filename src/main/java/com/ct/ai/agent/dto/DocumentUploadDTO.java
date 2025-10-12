package com.ct.ai.agent.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class DocumentUploadDTO {
    private MultipartFile file;
    private String filename; // 可选：自定义文件名
}