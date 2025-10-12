# AI智能体项目说明文档

## 项目概述

本项目是一个基于Spring AI框架开发的AI智能体系统，具备工具调用能力、任务规划能力和复杂问题拆解能力。智能体可通过调用文件操作、PDF生成、资源下载等工具，自动完成用户提出的各类任务需求，支持同步和流式交互模式。

## 核心功能

1. **多工具集成**：提供文件读写、PDF生成、网络资源下载等实用工具
2. **智能任务处理**：能将复杂任务拆解为可执行步骤，自动选择最优工具组合
3. **会话管理**：支持多会话上下文维护，确保任务处理的连贯性
4. **灵活交互**：提供同步响应和流式响应两种交互模式，适应不同场景需求
5. **错误处理**：完善的异常处理机制，包含连续失败降级策略

## 技术架构

- **基础框架**：Spring Boot
- **AI集成**：Spring AI
- **文件处理**：Hutool工具类
- **PDF生成**：iTextPDF
- **会话存储**：Redis
- **响应格式**：统一封装的BaseResponse

## 核心组件说明

### 1. 智能体核心

- **MyAgent**：继承自ToolCallAgent，实现了具备工具调用能力的智能体，包含系统提示词和任务处理逻辑
- **ToolCallAgent**：基于ReAct模式的工具调用智能体基类，实现"思考-行动"循环逻辑

### 2. 工具组件

| 工具类 | 功能描述 | 主要方法 |
|--------|----------|----------|
| FileOperationTool | 文件读写工具 | readFile(读取文件)、writeFile(写入文件) |
| PDFGenerationTool | PDF生成工具 | generatePDF(生成带元数据和页码的PDF) |
| ResourceDownloadTool | 资源下载工具 | downloadResource(从URL下载文件) |
| WebSearchTool | 网络搜索工具 | （代码中未展示具体实现） |
| WebScrapingTool | 网页抓取工具 | （代码中未展示具体实现） |

### 3. 数据模型

- **BaseResponse**：全局统一响应结果封装类
- **ReportVO**：报告数据模型
- **AgentRequestDTO/AgentResponseDTO**：智能体请求/响应数据传输对象

## 配置说明

### 文件存储配置

文件默认存储在系统当前目录下的`tmp`文件夹，可通过修改`FileConstant`类中的`FILE_SAVE_DIR`常量调整：

```java
public final class FileConstant {
    public static String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
```

### 工具注册

所有工具通过`ToolRegister`类进行注册，新工具需在此处添加：

```java
@Bean
public ToolCallback[] allTools() {
    // 添加工具实例
    FileOperationTool fileOperationTool = new FileOperationTool();
    PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
    // ...其他工具
    
    return ToolCallbacks.from(
        fileOperationTool,
        pdfGenerationTool
        // ...其他工具
    );
}
```

## 使用方法

### 1. 智能体服务接口

```java
// 同步执行任务
String result = agentService.executeTask("用户提示词", "会话ID");

// 流式执行任务
SseEmitter emitter = agentService.executeTaskStream("用户提示词", "会话ID");
```

### 2. 工具调用示例

#### 读取文件

```java
// 工具自动调用，用户可通过自然语言触发："读取test.txt文件"
String content = fileOperationTool.readFile("test.txt");
```

#### 生成PDF

```java
// 生成包含指定内容的PDF文件
String result = pdfGenerationTool.generatePDF(
    "report.pdf", 
    "PDF正文内容", 
    "报告标题", 
    "作者名称"
);
```

#### 下载资源

```java
// 从指定URL下载资源
String result = resourceDownloadTool.downloadResource(
    "https://example.com/file.pdf", 
    "保存的文件名.pdf"
);
```

## 异常处理

系统定义了多种异常处理机制：

1. **参数错误**：通过`BaseResponse.paramError()`返回
2. **业务异常**：通过`BaseResponse.businessError()`返回
3. **系统异常**：通过`BaseResponse.systemError()`返回
4. **工具调用失败**：包含连续失败检测和降级策略，可配置最大失败次数和缓存降级

## 扩展指南

1. **添加新工具**：
    - 创建工具类并实现所需功能
    - 使用`@Tool`注解标记工具方法
    - 在`ToolRegister`中注册新工具

2. **自定义智能体行为**：
    - 继承`ToolCallAgent`类
    - 重写`think()`和`act()`方法自定义思考和行动逻辑
    - 修改系统提示词(SYSTEM_PROMPT)定义智能体角色

3. **添加新的响应格式**：
    - 扩展`BaseResponse`类
    - 新增响应类型的静态工厂方法

## 注意事项

1. 所有文件操作默认在`FILE_SAVE_DIR`下进行，确保程序对该目录有读写权限
2. 工具调用有超时设置，长时间运行的任务可能需要调整超时参数
3. 会话上下文通过Redis存储，需确保Redis服务可用
4. 新增工具时需注意参数校验，避免无效输入导致的异常

---

项目启动类：`AiAgentApplication.java`
通过`SpringApplication.run(AiAgentApplication.class, args)`启动应用