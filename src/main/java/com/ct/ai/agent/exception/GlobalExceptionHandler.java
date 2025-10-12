package com.ct.ai.agent.exception;

import com.ct.ai.agent.entity.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器：统一捕获并处理所有Controller层抛出的异常
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        // 记录业务异常日志（info级别，非错误）
        log.info("业务异常: {}", e.getMessage(), e);
        ErrorResponse error = new ErrorResponse(
                e.getCode(),
                e.getMessage(),
                request.getRequestURI()
        );
        // 根据错误码动态设置HTTP状态码（默认500）
        HttpStatus status = HttpStatus.resolve(e.getCode());
        return new ResponseEntity<>(error, status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理参数校验异常（如@Valid注解触发的校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        BindingResult bindingResult = e.getBindingResult();
        // 拼接所有字段的错误信息
        StringBuilder errorMsg = new StringBuilder("参数校验失败: ");
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errorMsg.append(fieldError.getField()).append("=").append(fieldError.getDefaultMessage()).append("; ");
        }
        log.warn(errorMsg.toString(), e); // 记录警告日志
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                errorMsg.toString(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理404异常（需在配置中开启throwExceptionIfNoHandlerFound）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在: {}", request.getRequestURI(), e);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "请求的接口不存在",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * 处理所有未捕获的异常（兜底处理）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        // 记录错误日志（error级别，需排查）
        log.error("系统异常: {}", e.getMessage(), e);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "系统繁忙，请稍后再试", // 生产环境避免返回具体错误信息
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}