package com.ct.ai.agent.llm.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 违禁词校验顾问
 * 作为 ChatClient 的拦截器，在用户请求发送至大模型前校验输入文本，过滤包含违禁词的内容
 * 实现 Spring AI 的 BaseAdvisor 接口，融入聊天客户端的请求处理链路
 */
@Slf4j
public class ForbiddenWordsAdvisor implements BaseAdvisor {

    /**
     * 违禁词集合
     * 默认为静态集合（示例用），生产环境建议通过构造器从配置中心/数据库动态加载
     */
    private final Set<String> forbiddenWords;

    /**
     * 预编译的违禁词正则表达式
     * 用于高效匹配文本中的违禁词，避免重复编译提升性能
     */
    private final Pattern forbiddenPattern;

    /**
     * 默认构造器：使用预设违禁词初始化
     * 适用于快速测试场景，生产环境建议使用动态加载方式
     */
    public ForbiddenWordsAdvisor() {
        this(Set.of("暴力", "色情", "毒品", "赌博", "诈骗", "敏感词"));
    }

    /**
     * 带参构造器：支持外部注入违禁词集合
     * 可灵活从配置文件或数据库加载违禁词，便于动态更新
     *
     * @param forbiddenWords 违禁词集合，若为null则初始化空集合
     */
    public ForbiddenWordsAdvisor(Set<String> forbiddenWords) {
        this.forbiddenWords = forbiddenWords != null ? forbiddenWords : Collections.emptySet();
        this.forbiddenPattern = buildForbiddenPattern(this.forbiddenWords);
        log.info("违禁词校验顾问初始化，加载违禁词数量：{}", this.forbiddenWords.size());
    }

    /**
     * 构建违禁词匹配正则表达式
     * 1. 对每个违禁词进行转义（处理特殊字符如"*"、"."等）
     * 2. 使用"|"连接所有违禁词，实现"任一匹配即拦截"的逻辑
     * 3. 若违禁词为空，返回匹配空字符串的正则（避免后续校验报错）
     *
     * @param forbiddenWords 违禁词集合
     * @return 预编译的正则表达式对象
     */
    private Pattern buildForbiddenPattern(Set<String> forbiddenWords) {
        if (forbiddenWords.isEmpty()) {
            log.warn("未配置任何违禁词，将跳过违禁词校验");
            return Pattern.compile("^$"); // 匹配空字符串，始终返回false
        }

        StringBuilder patternBuilder = new StringBuilder();
        for (String word : forbiddenWords) {
            if (!patternBuilder.isEmpty()) {
                patternBuilder.append("|");
            }
            patternBuilder.append(Pattern.quote(word)); // 转义特殊字符
        }
        return Pattern.compile(patternBuilder.toString());
    }

    /**
     * 检查文本中是否包含违禁词
     *
     * @param text 待校验的文本（用户输入内容）
     * @return true：包含违禁词；false：不包含违禁词
     */
    private boolean containsForbiddenWords(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return forbiddenPattern.matcher(text).find();
    }

    /**
     * 顾问执行顺序（值越小越先执行）
     * 设置为0：确保在其他业务顾问（如日志、记忆存储）之前执行，避免违规内容进入后续流程
     *
     * @return 执行顺序优先级
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 请求处理前的拦截逻辑（核心校验过程）
     * 在用户消息发送给大模型前执行，若包含违禁词则抛出异常中断请求
     *
     * @param chatClientRequest 聊天客户端请求对象（包含用户输入等信息）
     * @param advisorChain      顾问链（框架自动处理后续链路，无需手动调用）
     * @return 校验通过后的请求对象
     * @throws IllegalArgumentException 当输入包含违禁词时抛出
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 提取用户消息（仅校验用户输入，忽略系统消息和AI回复）
        UserMessage userMessage = chatClientRequest.prompt().getUserMessage();
        if (userMessage == null) {
            log.debug("请求中无用户消息，跳过违禁词校验");
            return chatClientRequest;
        }

        String userText = userMessage.getText();
        if (!StringUtils.hasText(userText)) {
            log.debug("用户输入为空，跳过违禁词校验");
            return chatClientRequest;
        }

        // 违禁词校验
        if (containsForbiddenWords(userText)) {
            log.warn("用户输入包含违禁词，已拦截（文本已脱敏）：{}", desensitizeText(userText));
            throw new IllegalArgumentException("输入包含不适当内容，请修改后重试");
        }

        // 校验通过：记录日志并标记上下文
        log.debug("用户输入违禁词校验通过，文本长度：{}", userText.length());
        chatClientRequest.context().put("forbiddenCheckPassed", true);
        return chatClientRequest;
    }

    /**
     * 响应返回后的处理逻辑（无业务操作，保持接口实现完整性）
     * 因违禁词校验仅需在请求发送前执行，此处直接返回响应对象
     *
     * @param chatClientResponse 聊天客户端响应对象
     * @param advisorChain       顾问链（框架自动处理后续链路）
     * @return 原响应对象
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    /**
     * 文本脱敏处理
     * 对包含违禁词的文本进行部分隐藏，避免日志中泄露完整敏感内容
     *
     * @param text 待脱敏的文本
     * @return 脱敏后的文本（首尾各保留3个字符，中间用***替换）
     */
    private String desensitizeText(String text) {
        if (text.length() <= 6) {
            return text.charAt(0) + "***" + text.charAt(text.length() - 1);
        }
        return text.substring(0, 3) + "***" + text.substring(text.length() - 3);
    }
}