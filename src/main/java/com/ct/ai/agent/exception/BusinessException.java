package com.ct.ai.agent.exception;

import lombok.Getter;

/**
 * 自定义业务异常（如参数错误、权限不足等）
 */
@Getter
public class BusinessException extends RuntimeException {
    // 业务错误码
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    // 常见业务错误的静态方法
    public static BusinessException invalidParam(String message) {
        return new BusinessException(400, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException systemError(String message) {
        return new BusinessException(500, message);
    }
}