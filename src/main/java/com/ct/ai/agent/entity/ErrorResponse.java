package com.ct.ai.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 全局统一错误响应体
 */
@Data
public class ErrorResponse {
    // 错误码
    private int code;
    // 错误信息
    private String message;
    // 发生错误的请求路径
    private String path;
    // 错误发生时间
    private LocalDateTime timestamp;

    public ErrorResponse(int code, String message, String path) {
        this.code = code;
        this.message = message;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}