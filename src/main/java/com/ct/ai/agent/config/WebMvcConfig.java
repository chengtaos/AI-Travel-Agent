package com.ct.ai.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 核心配置类
 * 实现 WebMvcConfigurer 接口，自定义 Spring MVC 的核心行为：
 * 1. 配置跨域（解决前后端分离场景下的跨域请求限制）
 * 2. 配置静态资源映射（指定静态文件、接口文档资源的访问路径）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置跨域请求规则
     * 解决前后端分离架构中，前端（如 Vue/React）与后端（Spring Boot）不同域名/端口下的请求拦截问题
     * （浏览器的同源策略会默认拦截跨域请求，需后端明确允许跨域规则）
     *
     * @param registry 跨域规则注册器，用于定义跨域匹配路径、允许的来源/方法/头信息等
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 匹配所有后端接口路径（/** 表示所有层级的路径，如 /ai/chat、/user/login）
                .allowedOriginPatterns("*") // 允许的请求来源：* 表示允许所有域名（生产环境建议替换为具体前端域名，如 "http://localhost:8080"）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的 HTTP 请求方法（覆盖常见 CRUD 及预检请求 OPTIONS）
                .allowedHeaders("*") // 允许的请求头：* 表示允许前端传递任意请求头（如 Token、Content-Type 等）
                .allowCredentials(true) // 是否允许携带 Cookie 等凭证信息（前后端需一致，前端请求需开启 withCredentials: true）
                .maxAge(3600); // 跨域请求的预检结果缓存时间（单位：秒），3600 表示1小时内无需重复发送预检请求（优化性能）
    }

    /**
     * 配置静态资源映射规则
     * 定义 "URL路径" 与 "服务器本地/类路径资源" 的对应关系，让浏览器能访问后端的静态文件（如图片、JS、接口文档）
     *
     * @param registry 资源处理器注册器，用于绑定 URL 路径和资源位置
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1. 映射项目内静态资源：URL 访问 /static/** 时，实际读取 classpath:/static/ 目录下的文件
        // （classpath:/static/ 对应项目 src/main/resources/static 目录，常用于存放前端静态资源如图片、CSS、JS）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        // 2. 映射 Knife4j 接口文档资源（Knife4j 是 Swagger 的增强版，用于生成接口文档）
        // URL 访问 /doc.html 时，读取 Knife4j 内置在 META-INF/resources/ 中的文档首页
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        // 3. 映射 Knife4j 依赖的 WebJars 资源（WebJars 是将前端库打包为 Jar 包的规范，如 Swagger 的 JS/CSS）
        // URL 访问 /webjars/** 时，读取 META-INF/resources/webjars/ 目录下的依赖资源（确保文档页面样式和交互正常）
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}