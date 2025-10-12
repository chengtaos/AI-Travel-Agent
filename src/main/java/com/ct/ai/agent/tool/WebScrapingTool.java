package com.ct.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.io.IOException;

/**
 * 网页内容抓取工具（基于Jsoup实现）
 * 功能：从指定URL抓取网页内容，过滤冗余标签，提取核心文本信息，适配AI工具调用场景
 */
@Slf4j
public class WebScrapingTool {

    // 网络请求配置（常量定义，集中管理超时参数）
    private static final int TIMEOUT_MILLIS = 10000; // 超时时间：10秒（避免长时间阻塞）
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"; // 模拟浏览器请求头，避免被反爬拦截

    /**
     * 抓取网页内容并提取核心文本
     * 过滤HTML标签、保留有效文本，避免返回冗余代码影响AI处理
     *
     * @param url 目标网页URL（如"https://example.com/article"）
     * @return 格式化的网页文本内容（去除HTML标签，保留段落结构）
     */
    @Tool(description = "抓取指定URL的网页内容，提取并返回核心文本信息（自动过滤HTML标签和冗余代码）")
    public String scrapeWebPage(
            @ToolParam(description = "目标网页的完整URL（必须以http://或https://开头）", required = true)
            String url) {
        // 1. 入参校验：确保URL格式合法
        validateUrl(url);
        log.info("[网页抓取工具] 开始抓取：URL={}", url);

        try {
            // 2. 发送HTTP请求获取网页内容（模拟浏览器请求，设置超时）
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT) // 模拟浏览器标识，提高兼容性
                    .timeout(TIMEOUT_MILLIS) // 设置超时，避免无限等待
                    .get();

            // 3. 提取并清洗内容：保留文本，去除HTML标签和多余空白
            String cleanedContent = cleanWebContent(doc);

            // 4. 处理内容过长场景：截断并提示（避免AI处理负担过重）
            String result = truncateIfNecessary(cleanedContent);
            log.info("[网页抓取工具] 抓取完成：URL={}，内容长度={}字符", url, result.length());
            return result;

        } catch (IOException e) {
            // 网络异常（连接超时、404等）
            String errorMsg = "抓取失败（网络错误）：" + e.getMessage();
            log.error("[网页抓取工具] URL={} 网络异常", url, e);
            return errorMsg;
        } catch (Exception e) {
            // 其他未知异常
            String errorMsg = "抓取失败：" + e.getMessage();
            log.error("[网页抓取工具] URL={} 处理异常", url, e);
            return errorMsg;
        }
    }

    /**
     * 校验URL格式合法性
     */
    private void validateUrl(String url) {
        Assert.hasText(url, "网页URL不能为空");
        Assert.isTrue(url.startsWith("http://") || url.startsWith("https://"),
                "URL格式不合法，必须以http://或https://开头");
    }

    /**
     * 清洗网页内容：去除HTML标签、保留文本和基本结构
     * 使用Jsoup的安全过滤机制，避免恶意脚本和冗余标签
     */
    private String cleanWebContent(Document doc) {
        // 1. 优先提取<body>标签内的内容（忽略<head>等无关部分）
        Element body = doc.body();
        if (body == null) {
            return "网页内容为空或格式异常";
        }

        // 2. 使用Safelist过滤标签：只保留文本相关标签（p、div、h1-h6等），去除script、style等
        Safelist safelist = Safelist.basic() // 基础安全列表：允许常见文本标签
                .addTags("h1", "h2", "h3", "h4", "h5", "h6") // 保留标题标签
                .addAttributes(":all", "class"); // 保留class属性（辅助识别结构）

        // 3. 转换为纯文本并清理空白字符（连续换行、空格等）
        String cleanedHtml = Jsoup.clean(body.html(), safelist);
        return cleanedHtml.replaceAll("\\s+", " ").trim(); // 合并空格，去除首尾空白
    }

    /**
     * 内容过长时截断并提示（避免AI处理压力过大）
     */
    private String truncateIfNecessary(String content) {
        final int MAX_LENGTH = 10000; // 最大保留长度：10000字符（可根据需求调整）
        if (content.length() <= MAX_LENGTH) {
            return content;
        }
        // 截断并添加提示
        return content.substring(0, MAX_LENGTH) + "\n\n[注：内容过长，已截断。完整内容需进一步处理]";
    }
}