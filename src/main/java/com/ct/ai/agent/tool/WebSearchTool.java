package com.ct.ai.agent.tool;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具（基于SearchAPI调用百度搜索引擎）
 * 功能：通过API接口执行百度搜索，提取并格式化返回结果，支持AI工具调用获取实时网络信息
 */
@Slf4j
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final String SEARCH_ENGINE = "baidu"; // 固定使用百度搜索引擎
    private static final int MAX_RESULT_COUNT = 5; // 最多返回5条结果，平衡信息完整性与处理效率

    // API密钥（通过构造函数注入，避免硬编码，支持不同环境配置）
    private final String apiKey;

    /**
     * 构造函数：初始化搜索工具的API密钥
     *
     * @param apiKey SearchAPI的访问密钥（从配置文件注入）
     */
    public WebSearchTool(String apiKey) {
        // 校验API密钥非空，避免初始化无效实例
        Assert.hasText(apiKey, "SearchAPI的apiKey不能为空，请配置有效的密钥");
        this.apiKey = apiKey;
    }

    /**
     * 执行百度网页搜索
     *
     * @param query 搜索关键词（如"2024年人工智能发展趋势"）
     * @return 格式化的搜索结果（包含标题、链接、摘要等关键信息）
     */
    @Tool(description = "通过百度搜索引擎获取实时网络信息，输入搜索关键词即可返回相关结果")
    public String searchWeb(
            @ToolParam(description = "搜索关键词（需具体明确，如'北京天气'、'Java最新特性'）", required = true)
            String query) {
        // 1. 校验搜索关键词非空
        Assert.hasText(query, "搜索关键词不能为空，请输入需要查询的内容");
        log.info("[网页搜索工具] 执行搜索：关键词={}", query);

        try {
            // 2. 构建API请求参数
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("q", query); // 搜索关键词
            requestParams.put("api_key", this.apiKey); // 接口鉴权密钥
            requestParams.put("engine", SEARCH_ENGINE); // 指定搜索引擎为百度

            // 3. 调用SearchAPI执行搜索（使用Hutool的HttpUtil简化HTTP请求）
            String apiResponse = HttpUtil.get(SEARCH_API_URL, requestParams);
            log.debug("[网页搜索工具] API返回原始数据：{}", apiResponse);

            // 4. 解析并提取有效结果
            return parseSearchResults(apiResponse);

        } catch (Exception e) {
            // 5. 捕获异常并返回友好提示
            String errorMsg = "搜索失败：" + e.getMessage();
            log.error("[网页搜索工具] 关键词={} 搜索异常", query, e);
            return errorMsg;
        }
    }

    /**
     * 解析API返回结果，提取关键信息并格式化
     *
     * @param apiResponse 搜索API返回的原始JSON字符串
     * @return 格式化的结果文本（包含标题、链接、摘要）
     */
    private String parseSearchResults(String apiResponse) {
        // 解析JSON响应
        JSONObject responseJson = JSONUtil.parseObj(apiResponse);

        // 提取有机搜索结果（排除广告等非自然结果）
        JSONArray organicResults = responseJson.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            return "未找到相关搜索结果，请尝试其他关键词";
        }

        // 截取前MAX_RESULT_COUNT条结果，避免信息过载
        int actualCount = Math.min(organicResults.size(), MAX_RESULT_COUNT);
        List<Object> topResults = organicResults.subList(0, actualCount);

        // 格式化每条结果：提取标题、链接、摘要（忽略冗余字段，提升可读性）
        return topResults.stream()
                .map(result -> {
                    JSONObject resultJson = (JSONObject) result;
                    return String.format(
                            "标题：%s\n链接：%s\n摘要：%s\n",
                            resultJson.getStr("title", "无标题"),
                            resultJson.getStr("link", "无链接"),
                            resultJson.getStr("snippet", "无摘要")
                    );
                })
                .collect(Collectors.joining("\n---\n")); // 用分隔线区分不同结果
    }
}