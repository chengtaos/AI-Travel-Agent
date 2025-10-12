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
     *
     * @param prompt 用户请求内容（必填，不可为空或纯空格）
     * @return 统一响应体（AgentResponseVO）：包含请求状态（success/error）和处理结果
     */
    @PostMapping("/execute")
    public ResponseEntity<AgentResponseVO> executeTask(@RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt) {
        log.info("【基础同步接口】接收到智能体请求，prompt长度：{}字符", prompt.trim().length());
        String result = agentService.executeTask(prompt.trim());
        AgentResponseVO successResponse = new AgentResponseVO()
                .setStatus("success")
                .setResult(result)
                .setMessage("处理完成");
        return ResponseEntity.ok(successResponse);
    }


    /**
     * 高级同步接口：处理带额外配置的复杂智能体请求
     * 适用场景：需自定义智能体行为（如指定角色、携带会话上下文、开启工具调用）
     *
     * @param request 智能体请求对象（包含用户输入+配置参数，需符合校验规则）
     * @return 统一响应体（AgentResponseVO）：包含状态、处理结果及配置相关信息
     */
    @PostMapping("/execute/advanced")
    public ResponseEntity<AgentResponseVO> executeAdvancedTask(@Valid @RequestBody AgentRequestVO request) {
        log.info("【高级同步接口】接收到智能体请求，请求参数：{}", request);
        AgentResponseVO response = agentService.executeAdvancedTask(request);
        return ResponseEntity.ok(response);
    }


    /**
     * 基础流式接口：处理耗时智能体请求，逐段返回结果
     * 适用场景：生成长文本（如报告、文档）、复杂计算等耗时任务，避免前端长时间等待
     *
     * @param prompt 用户请求内容（必填）
     * @return SseEmitter：SSE（Server-Sent Events）连接对象，用于实时推送流式数据
     */
    @GetMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeTaskStream(@RequestParam @NotBlank(message = "用户请求内容不能为空") String prompt) {
        log.info("【基础流式接口】接收到智能体请求，prompt长度：{}字符", prompt.trim().length());
        // 流式接口仅处理SSE连接创建失败的场景，业务异常由服务层抛出后全局处理器记录
        try {
            return agentService.executeTaskStream(prompt.trim());
        } catch (Exception e) {
            log.error("【基础流式接口】创建流式连接失败", e);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                // 向前端推送错误事件（符合SSE协议）
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式连接创建失败：" + e.getMessage()));
                errorEmitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("【基础流式接口】发送错误事件失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 高级流式接口：处理带额外配置的耗时智能体请求，逐段返回结果
     * 适用场景：需定制智能体行为的耗时任务（如带会话上下文的长对话、工具调用的流式结果）
     *
     * @param request 智能体请求对象（含用户输入+配置参数）
     * @return SseEmitter：SSE连接对象，用于实时推送流式数据
     */
    @PostMapping(value = "/execute/stream/advance", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeAdvancedTaskStream(@Valid @RequestBody AgentRequestVO request) {
        log.info("【高级流式接口】接收到智能体请求，请求参数：{}", request);
        try {
            return agentService.executeAdvancedTaskStream(request);
        } catch (Exception e) {
            log.error("【高级流式接口】创建流式连接失败", e);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式连接创建失败：" + e.getMessage()));
                errorEmitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("【高级流式接口】发送错误事件失败", ex);
            }
            return errorEmitter;
        }
    }


    /**
     * 智能体状态查询接口
     * 用途：监控智能体运行状态（如在线状态、当前会话数、资源占用情况），排查服务可用性
     */
    @GetMapping("/status")
    public ResponseEntity<AgentResponseVO> getAgentStatus() {
        log.info("【状态查询接口】接收到智能体状态查询请求");
        AgentResponseVO statusResponse = agentService.getAgentStatus();
        return ResponseEntity.ok(statusResponse);
    }


    /**
     * 智能体状态重置接口
     * 用途：智能体异常时（如会话堆积、资源泄漏），重置状态以恢复服务
     */
    @PostMapping("/reset")
    public ResponseEntity<AgentResponseVO> resetAgent() {
        log.info("【重置接口】接收到智能体重置请求");
        AgentResponseVO resetResponse = agentService.resetAgent();
        return ResponseEntity.ok(resetResponse);
    }


    /**
     * 流式连接关闭接口：主动关闭指定会话的流式连接
     * 用途：前端主动断开连接，避免服务器资源泄漏
     */
    @PostMapping("/stream/close/{sessionId}")
    public ResponseEntity<AgentResponseVO> closeStream(
            @PathVariable
            @NotBlank(message = "会话ID不能为空")
            String sessionId) {

        log.info("【关闭流式连接接口】接收到关闭请求，sessionId：{}", sessionId);
        AgentResponseVO closeResponse = agentService.closeStream(sessionId.trim());
        return ResponseEntity.ok(closeResponse);
    }
}