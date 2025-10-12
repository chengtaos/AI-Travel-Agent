package com.ct.ai.agent.config;

import com.ct.ai.agent.vo.BaseResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpringDoc配置类（整合Swagger/OpenAPI）
 * 作用：统一配置接口文档的基础信息、通用响应模型、全局接口响应规则
 */
@Configuration // 标记为Spring配置类，启动时自动加载
public class SpringDocConfig {

    /**
     * 配置OpenAPI基础信息（文档标题、版本、服务地址等）
     * 最终会在Swagger UI页面展示这些基础信息
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 1. 配置文档基础信息（标题、版本）
                .info(new Info()
                        .title("AI Agent 接口文档") // 文档标题，清晰说明接口用途
                        .version("v1.0.0")) // 接口版本，便于版本管理
                // 2. 配置服务地址（前端调用接口时的基础路径）
                .servers(List.of(
                        new Server().url("/").description("本地开发环境"))) // 本地环境默认根路径
                // 3. 配置文档组件（此处主要注册通用响应模型BaseResponse）
                .components(new Components()
                        .schemas(buildSchemas()));
    }

    /**
     * 构建通用响应模型（将BaseResponse注册到Swagger组件中）
     * 目的：让所有接口的响应都能复用BaseResponse模型，避免重复定义
     */
    private Map<String, Schema> buildSchemas() {
        Map<String, Schema> schemaMap = new HashMap<>(); // 存储注册的模型

        // 解析BaseResponse类，转换为Swagger可识别的Schema
        ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(
                        new AnnotatedType(BaseResponse.class).resolveAsRef(false));

        // 若解析成功，将BaseResponse模型存入Map
        if (resolvedSchema.schema != null) {
            schemaMap.put("BaseResponse", resolvedSchema.schema);
        }

        return schemaMap;
    }

    /**
     * 全局接口响应自定义配置
     * 作用：为所有接口自动添加通用响应（200成功、400参数错、500系统错），无需每个接口单独写
     */
    @Bean
    public GlobalOpenApiCustomizer globalOpenApiCustomizer() {
        // 自定义OpenAPI处理器，遍历所有接口路径和操作
        return openApi -> {
            // 1. 判断是否有接口路径，避免空指针
            if (openApi.getPaths() != null) {
                // 遍历所有接口路径（如/agent/execute、/agent/status）
                openApi.getPaths().forEach((path, pathItem) -> {
                    // 2. 遍历路径下的所有操作（GET/POST等请求方法）
                    if (pathItem.readOperations() != null) {
                        pathItem.readOperations().forEach(operation -> {
                            // 初始化响应容器（若接口未定义响应，先创建空容器）
                            if (operation.getResponses() == null) {
                                operation.setResponses(new ApiResponses());
                            }

                            // 3. 为接口添加200成功响应（复用BaseResponse模型）
                            ApiResponse okResponse = operation.getResponses().get("200");
                            if (okResponse == null) { // 若接口未自定义200响应，添加默认的
                                okResponse = new ApiResponse()
                                        .description("操作成功") // 响应描述
                                        .content(new Content() // 响应内容格式（JSON）
                                                .addMediaType("application/json", new MediaType()
                                                        // 引用已注册的BaseResponse模型
                                                        .schema(new Schema<>().$ref("#/components/schemas/BaseResponse"))));
                                operation.getResponses().addApiResponse("200", okResponse);
                            }

                            // 4. 为接口添加400参数错误响应
                            operation.getResponses().addApiResponse("400", new ApiResponse()
                                    .description("请求参数错误") // 如必填参数为空、格式非法
                                    .content(new Content()
                                            .addMediaType("application/json", new MediaType()
                                                    .schema(new Schema<>().$ref("#/components/schemas/BaseResponse")))));

                            // 5. 为接口添加500系统内部错误响应
                            operation.getResponses().addApiResponse("500", new ApiResponse()
                                    .description("系统内部错误") // 如服务调用失败、数据库异常
                                    .content(new Content()
                                            .addMediaType("application/json", new MediaType()
                                                    .schema(new Schema<>().$ref("#/components/schemas/BaseResponse")))));
                        });
                    }
                });
            }
        };
    }
}