package com.ct.ai.agent.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegister {
    @Value("${search.api.key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {
        //TODO 添加工具类
        FileOperationTool fileOperationTool = new FileOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool=new WebScrapingTool();

        // 使用反射，过滤出所有带有 @Tool 注解的方法，并将它们转换为 ToolCallback 实例。
        return ToolCallbacks.from(
                fileOperationTool,
                pdfGenerationTool,
                webSearchTool,
                webScrapingTool
        );
    }
}
