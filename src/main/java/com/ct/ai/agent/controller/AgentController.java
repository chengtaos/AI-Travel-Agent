package com.ct.ai.agent.controller;

import com.ct.ai.agent.service.AgentService;
import com.ct.ai.agent.vo.AgentRequestVO;
import com.ct.ai.agent.vo.AgentResponseVO;
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
     * 优化点：自动生成会话ID，支持后续状态查询
     *
     * @param prompt    用户请求内容（必填，不可为空或纯空格）
     * @param sessionId 可选参数：已有会话ID（复用会话），无则自动生成
     * @return 统一响应体（AgentResponseVO）：包含会话ID、请求状态和处理结果
     */
    @PostMapping("/execute")
    public ResponseEntity<AgentResponseVO> executeTask(
            @RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt,
            @RequestParam(required = false) String sessionId) {

        // 处理会话ID：无则生成新ID（UUID保证唯一性）
        String finalSessionId = sessionId != null ? sessionId.trim() : UUID.randomUUID().toString();
        log.info("【基础同步接口】接收到请求，sessionId：{}，prompt长度：{}字符",
                finalSessionId, prompt.trim().length());

        try {
            String result = agentService.executeTask(prompt.trim());
            // 响应中携带会话ID，方便前端后续操作（如查询状态、关闭连接）
            AgentResponseVO successResponse = new AgentResponseVO()
                    .setStatus("success")
                    .setResult(result)
                    .setMessage("处理完成")
                    .setSessionId(finalSessionId); // 新增：返回会话ID
            return ResponseEntity.ok(successResponse);

        } catch (Exception e) {
            log.error("【基础同步接口】处理失败，sessionId：{}", finalSessionId, e);
            // 错误响应标准化：包含会话ID和具体错误信息
            AgentResponseVO errorResponse = new AgentResponseVO()
                    .setStatus("error")
                    .setResult(null)
                    .setMessage("处理失败：" + e.getMessage())
                    .setSessionId(finalSessionId);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 高级同步接口：处理带额外配置的复杂智能体请求
     * 适用场景：需自定义智能体行为（如指定角色、携带会话上下文、开启工具调用）
     * 优化点：支持会话ID复用，请求参数校验增强
     *
     * @param request 智能体请求对象（包含用户输入+配置参数+会话ID）
     * @return 统一响应体（AgentResponseVO）：包含状态、处理结果及会话信息
     */
    @PostMapping("/execute/advanced")
    public ResponseEntity<AgentResponseVO> executeAdvancedTask(@Valid @RequestBody AgentRequestVO request) {
        // 补全会话ID：请求中无则生成新ID
        if (request.getSessionId() == null) {
            request.setSessionId(UUID.randomUUID().toString());
        }
        log.info("【高级同步接口】接收到请求，参数：{}", request);

        try {
            AgentResponseVO response = agentService.executeAdvancedTask(request);
            // 确保响应携带会话ID（与请求一致）
            response.setSessionId(request.getSessionId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("【高级同步接口】处理失败，sessionId：{}", request.getSessionId(), e);
            AgentResponseVO errorResponse = new AgentResponseVO()
                    .setStatus("error")
                    .setResult(null)
                    .setMessage("处理失败：" + e.getMessage())
                    .setSessionId(request.getSessionId());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 基础流式接口：处理耗时智能体请求，逐段返回结果
     * 适用场景：生成长文本（如报告、文档）、复杂计算等耗时任务
     * 优化点：支持会话ID复用，错误事件标准化
     *
     * @param prompt    用户请求内容（必填）
     * @param sessionId 可选参数：已有会话ID（复用会话），无则自动生成
     * @return SseEmitter：SSE连接对象，推送流式数据（含标准错误事件）
     */
    @GetMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeTaskStream(
            @RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt,
            @RequestParam(required = false) String sessionId) {

        String finalSessionId = sessionId != null ? sessionId.trim() : UUID.randomUUID().toString();
        log.info("【基础流式接口】接收到请求，sessionId：{}，prompt长度：{}字符",
                finalSessionId, prompt.trim().length());

        try {
            // 服务层已处理连接生命周期，此处仅返回发射器
            return agentService.executeTaskStream(prompt.trim());

        } catch (Exception e) {
            log.error("【基础流式接口】创建连接失败，sessionId：{}", finalSessionId, e);
            SseEmitter errorEmitter = new SseEmitter(3000L); // 3秒超时，避免连接挂起
            try {
                // 标准错误事件：name="error"，便于前端统一捕获
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .id(finalSessionId) // 事件ID绑定会话ID
                        .data("流式连接创建失败：" + e.getMessage()));
                errorEmitter.completeWithError(e); // 标记连接错误完成
            } catch (Exception ex) {
                log.error("【基础流式接口】发送错误事件失败，sessionId：{}", finalSessionId, ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 高级流式接口：处理带额外配置的耗时智能体请求
     * 适用场景：需定制智能体行为的耗时任务（如带会话上下文的长对话）
     * 优化点：请求参数补全会话ID，错误处理标准化
     *
     * @param request 智能体请求对象（含用户输入+配置参数+会话ID）
     * @return SseEmitter：SSE连接对象，实时推送流式数据
     */
    @PostMapping(value = "/execute/stream/advance", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeAdvancedTaskStream(@Valid @RequestBody AgentRequestVO request) {
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
                        .data("流式连接创建失败：" + e.getMessage()));
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
     * 用途：监控单个会话状态或全局资源占用，排查服务问题
     *
     * @param sessionId 可选参数：指定会话ID（查询单个会话），无则查询全局
     * @return 统一响应体：包含单个会话详情或全局汇总信息
     */
    @GetMapping("/status")
    public ResponseEntity<AgentResponseVO> getAgentStatus(
            @RequestParam(required = false) String sessionId) {

        log.info("【状态查询接口】接收到请求，查询sessionId：{}", sessionId);
        try {
            AgentResponseVO statusResponse;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // 模式1：查询指定会话状态
                statusResponse = agentService.getAgentStatus(sessionId.trim());
            } else {
                // 模式2：查询全局活跃会话汇总
                statusResponse = agentService.getAgentStatus();
            }
            return ResponseEntity.ok(statusResponse);

        } catch (Exception e) {
            log.error("【状态查询接口】查询失败，sessionId：{}", sessionId, e);
            AgentResponseVO errorResponse = new AgentResponseVO()
                    .setStatus("error")
                    .setResult(null)
                    .setMessage("查询失败：" + e.getMessage())
                    .setSessionId(sessionId);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 智能体状态重置接口（增强版）
     * 支持两种模式：1. 传sessionId重置指定会话；2. 不传提示需指定会话（避免误操作全局）
     * 用途：单个会话异常时重置，避免影响其他会话
     *
     * @param sessionId 可选参数：指定会话ID（必传，否则返回提示）
     * @return 统一响应体：重置结果（成功/失败/提示）
     */
    @PostMapping("/reset")
    public ResponseEntity<AgentResponseVO> resetAgent(
            @RequestParam(required = false) String sessionId) {

        log.info("【重置接口】接收到请求，重置sessionId：{}", sessionId);
        try {
            AgentResponseVO resetResponse;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // 模式1：重置指定会话
                resetResponse = agentService.resetAgent(sessionId.trim());
            } else {
                // 模式2：未传会话ID，返回提示（避免误重置全局）
                resetResponse = new AgentResponseVO()
                        .setStatus("warning")
                        .setResult(null)
                        .setMessage("请传入sessionId，指定需重置的会话");
            }
            return ResponseEntity.ok(resetResponse);

        } catch (Exception e) {
            log.error("【重置接口】重置失败，sessionId：{}", sessionId, e);
            AgentResponseVO errorResponse = new AgentResponseVO()
                    .setStatus("error")
                    .setResult(null)
                    .setMessage("重置失败：" + e.getMessage())
                    .setSessionId(sessionId);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 流式连接关闭接口：主动关闭指定会话的流式连接
     * 优化点：增强参数校验，错误响应携带会话ID
     *
     * @param sessionId 会话ID（路径参数，必传）
     * @return 统一响应体：关闭结果（成功/失败/会话不存在）
     */
    @PostMapping("/stream/close/{sessionId}")
    public ResponseEntity<AgentResponseVO> closeStream(
            @PathVariable @NotBlank(message = "会话ID不能为空") String sessionId) {

        String finalSessionId = sessionId.trim();
        log.info("【关闭流式连接接口】接收到请求，sessionId：{}", finalSessionId);

        try {
            AgentResponseVO closeResponse = agentService.closeStream(finalSessionId);
            return ResponseEntity.ok(closeResponse);

        } catch (Exception e) {
            log.error("【关闭流式连接接口】关闭失败，sessionId：{}", finalSessionId, e);
            AgentResponseVO errorResponse = new AgentResponseVO()
                    .setStatus("error")
                    .setResult(null)
                    .setMessage("关闭失败：" + e.getMessage())
                    .setSessionId(finalSessionId);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}