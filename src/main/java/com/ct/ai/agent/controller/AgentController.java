package com.ct.ai.agent.controller;

import com.ct.ai.agent.service.AgentService;
import com.ct.ai.agent.dto.AgentRequestDTO;
import com.ct.ai.agent.dto.AgentResponseDTO;
import com.ct.ai.agent.vo.BaseResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * AI智能体控制器
 * 提供智能体的交互接口，涵盖三大核心能力：
 * 1. 同步响应：处理简单/复杂请求，返回完整结果
 * 2. 流式响应：处理耗时任务（如生成长文本），逐段返回结果优化体验
 * 3. 状态管理：查询智能体状态、重置状态、关闭流式连接，保障服务稳定性
 */
@RestController
@RequestMapping("/agent")
@Slf4j
public class AgentController {
    @Resource
    private AgentService agentService;


    /**
     * 基础同步接口：处理简单的智能体请求
     * 适用场景：仅需传入用户输入内容，无需额外配置（如“回答问题”“生成简短文本”）
     */
    @PostMapping("/execute")
    public ResponseEntity<BaseResponse<AgentResponseDTO>> executeTask(
            @RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt,
            @RequestParam(required = false) String sessionId) {
        String finalSessionId = sessionId != null ? sessionId.trim() : UUID.randomUUID().toString();
        log.info("【基础同步接口】接收到请求，sessionId：{}，prompt长度：{}字符",
                finalSessionId, prompt.trim().length());

        try {
            String result = agentService.executeTask(prompt.trim(), sessionId);
            AgentResponseDTO responseData = new AgentResponseDTO()
                    .setStatus("success")
                    .setResult(result)
                    .setMessage("处理完成")
                    .setSessionId(finalSessionId);
            return ResponseEntity.ok(BaseResponse.success(responseData));

        } catch (Exception e) {
            log.error("【基础同步接口】处理失败，sessionId：{}", finalSessionId, e);
            return ResponseEntity.status(500)
                    .body(BaseResponse.error(5001, "处理失败：" + e.getMessage()));
        }
    }


    /**
     * 高级同步接口：处理带额外配置的复杂智能体请求
     * 适用场景：需自定义智能体行为（如指定角色、携带会话上下文、开启工具调用）
     */
    @PostMapping("/execute/advanced")
    public ResponseEntity<BaseResponse<AgentResponseDTO>> executeAdvancedTask(@Valid @RequestBody AgentRequestDTO request) {
        // 补全会话ID：请求中无则生成新ID
        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }
        log.info("【高级同步接口】接收到请求，参数：{}", request);

        try {
            AgentResponseDTO response = agentService.executeAdvancedTask(request);
            response.setSessionId(request.getSessionId());
            return ResponseEntity.ok(BaseResponse.success(response));

        } catch (Exception e) {
            log.error("【高级同步接口】处理失败，sessionId：{}", request.getSessionId(), e);
            return ResponseEntity.status(500)
                    .body(BaseResponse.error(5001, "处理失败：" + e.getMessage()));
        }
    }


    /**
     * 基础流式接口：处理耗时智能体请求，逐段返回结果
     * 适用场景：生成长文本（如报告、文档）、复杂计算等耗时任务
     */
    @GetMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeTaskStream(
            @RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt,
            @RequestParam(required = false) String sessionId) {

        String finalSessionId = sessionId != null ? sessionId.trim() : UUID.randomUUID().toString();
        log.info("【基础流式接口】接收到请求，sessionId：{}，prompt长度：{}字符",
                finalSessionId, prompt.trim().length());
        try {
            return agentService.executeTaskStream(prompt.trim(), finalSessionId);
        } catch (Exception e) {
            log.error("【基础流式接口】创建连接失败，sessionId：{}", finalSessionId, e);
            SseEmitter errorEmitter = new SseEmitter(3000L); // 3秒超时，避免连接挂起
            try {
                // 流式响应也用BaseResponse包装错误信息
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .id(finalSessionId)
                        .data(BaseResponse.error(5001, "流式连接创建失败：" + e.getMessage())));
                errorEmitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("【基础流式接口】发送错误事件失败，sessionId：{}", finalSessionId, ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 高级流式接口：处理带额外配置的耗时智能体请求
     * 适用场景：需定制智能体行为的耗时任务（如带会话上下文的长对话）
     */
    @PostMapping(value = "/execute/stream/advance", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeAdvancedTaskStream(@Valid @RequestBody AgentRequestDTO request) {
        // 补全会话ID：确保每个流式连接有唯一标识
        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }
        log.info("【高级流式接口】接收到请求，参数：{}", request);

        try {
            return agentService.executeAdvancedTaskStream(request);

        } catch (Exception e) {
            log.error("【高级流式接口】创建连接失败，sessionId：{}", request.getSessionId(), e);
            SseEmitter errorEmitter = new SseEmitter(3000L);
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .id(request.getSessionId())
                        .data(BaseResponse.error(5001, "流式连接创建失败：" + e.getMessage())));
                errorEmitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("【高级流式接口】发送错误事件失败，sessionId：{}", request.getSessionId(), ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 智能体状态查询接口（增强版）
     * 支持两种模式：1. 传sessionId查询指定会话状态；2. 不传查询所有活跃会话汇总
     */
    @GetMapping("/status")
    public ResponseEntity<BaseResponse<AgentResponseDTO>> getAgentStatus(
            @RequestParam(required = false) String sessionId) {

        log.info("【状态查询接口】接收到请求，查询sessionId：{}", sessionId);
        try {
            AgentResponseDTO statusResponse;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // 模式1：查询指定会话状态
                statusResponse = agentService.getAgentStatus(sessionId.trim());
            } else {
                // 模式2：查询全局活跃会话汇总
                statusResponse = agentService.getAgentStatus();
            }
            return ResponseEntity.ok(BaseResponse.success(statusResponse));

        } catch (Exception e) {
            log.error("【状态查询接口】查询失败，sessionId：{}", sessionId, e);
            return ResponseEntity.status(500)
                    .body(BaseResponse.error(5001, "查询失败：" + e.getMessage()));
        }
    }


    /**
     * 智能体状态重置接口（增强版）
     * 支持两种模式：1. 传sessionId重置指定会话；2. 不传提示需指定会话（避免误操作全局）
     */
    @PostMapping("/reset")
    public ResponseEntity<BaseResponse<AgentResponseDTO>> resetAgent(
            @RequestParam(required = false) String sessionId) {

        log.info("【重置接口】接收到请求，重置sessionId：{}", sessionId);
        try {
            AgentResponseDTO resetResponse;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // 模式1：重置指定会话
                resetResponse = agentService.resetAgent(sessionId.trim());
            } else {
                // 模式2：未传会话ID，返回提示
                resetResponse = new AgentResponseDTO()
                        .setStatus("warning")
                        .setResult(null)
                        .setMessage("请传入sessionId，指定需重置的会话");
            }
            return ResponseEntity.ok(BaseResponse.success(resetResponse));

        } catch (Exception e) {
            log.error("【重置接口】重置失败，sessionId：{}", sessionId, e);
            return ResponseEntity.status(500)
                    .body(BaseResponse.error(5001, "重置失败：" + e.getMessage()));
        }
    }


    /**
     * 流式连接关闭接口：主动关闭指定会话的流式连接
     */
    @PostMapping("/stream/close/{sessionId}")
    public ResponseEntity<BaseResponse<AgentResponseDTO>> closeStream(
            @PathVariable @NotBlank(message = "会话ID不能为空") String sessionId) {

        String finalSessionId = sessionId.trim();
        log.info("【关闭流式连接接口】接收到请求，sessionId：{}", finalSessionId);

        try {
            AgentResponseDTO closeResponse = agentService.closeStream(finalSessionId);
            return ResponseEntity.ok(BaseResponse.success(closeResponse));

        } catch (Exception e) {
            log.error("【关闭流式连接接口】关闭失败，sessionId：{}", finalSessionId, e);
            return ResponseEntity.status(500)
                    .body(BaseResponse.error(5001, "关闭失败：" + e.getMessage()));
        }
    }
}