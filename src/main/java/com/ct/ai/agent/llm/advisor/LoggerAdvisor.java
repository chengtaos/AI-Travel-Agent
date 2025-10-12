package com.ct.ai.agent.llm.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

/**
 * 日志记录
 * 实现 Spring AI 的 BaseAdvisor 接口，用于在 AI 对话链路中记录请求和响应日志
 * 核心作用：跟踪用户输入与 AI 输出，便于调试、问题排查和对话审计
 */
@Slf4j
public class LoggerAdvisor implements BaseAdvisor {

    /**
     * 日志记录顺序（值越小越先执行）
     * 设置为 2：确保在违禁词校验（order=0）等前置校验通过后再记录日志，避免记录违规内容
     */
    @Override
    public int getOrder() {
        return 2;
    }

    /**
     * 请求发送前的日志记录
     * 在用户消息发送给大模型前，记录用户输入内容（已通过前置校验）
     *
     * @param chatClientRequest AI 聊天请求对象（包含用户输入、上下文等信息）
     * @param advisorChain 顾问链（框架自动处理后续链路，无需手动调用）
     * @return 原请求对象（不修改请求内容）
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 提取用户消息（仅记录用户输入，忽略系统消息）
        UserMessage userMessage = chatClientRequest.prompt().getUserMessage();
        if (userMessage == null) {
            log.debug("请求中无用户消息，跳过请求日志记录");
            return chatClientRequest;
        }

        String userInput = userMessage.getText();
        if (StringUtils.hasText(userInput)) {
            // 记录用户输入（长度过长时截断，避免日志膨胀）
            log.info("AI 对话请求 - 用户输入: {}", truncateText(userInput));
        } else {
            log.debug("AI 对话请求 - 用户输入为空");
        }

        return chatClientRequest;
    }

    /**
     * 响应返回后的日志记录
     * 在大模型返回结果后，记录 AI 输出内容，便于跟踪对话结果
     *
     * @param chatClientResponse AI 聊天响应对象（包含 AI 输出、元数据等信息）
     * @param advisorChain 顾问链（框架自动处理后续链路）
     * @return 原响应对象（不修改响应内容）
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 校验响应结果是否存在
        if (chatClientResponse.chatResponse() == null
                || chatClientResponse.chatResponse().getResult() == null
                || chatClientResponse.chatResponse().getResult().getOutput() == null) {
            log.warn("AI 对话响应为空，跳过响应日志记录");
            return chatClientResponse;
        }

        String aiOutput = chatClientResponse.chatResponse().getResult().getOutput().getText();
        if (StringUtils.hasText(aiOutput)) {
            // 记录 AI 输出（长度过长时截断）
            log.info("AI 对话响应 - AI 输出: {}", truncateText(aiOutput));
        } else {
            log.debug("AI 对话响应 - AI 输出为空");
        }

        return chatClientResponse;
    }

    /**
     * 文本截断工具方法
     * 当日志文本过长时（超过 200 字符）截断并添加省略号，避免日志内容过大
     *
     * @param text 待处理的文本（用户输入或 AI 输出）
     * @return 截断后的文本（短文本不处理，长文本保留前 200 字符）
     */
    private String truncateText(String text) {
        int maxLength = 200;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[文本过长，已截断]";
    }
}