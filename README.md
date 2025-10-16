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

### 1. Agent 架构设计

本项目采用 **分层继承 + ReAct 推理循环 + 会话治理** 的智能体架构，具备良好的可扩展性与生产级稳定性。整体架构基于面向对象设计与工厂模式构建，支持多会话并发、任务自动拆解与工具动态调用。

#### 架构层级

```
+------------------------+
|       MyAgent          |  ← 业务定制Agent（角色、提示词、行为）
+------------------------+
           ↑
+------------------------+
|    ToolCallAgent       |  ← 实现工具调用逻辑（think/act）
+------------------------+
           ↑
+------------------------+
|      ReActAgent        |  ← 定义“思考-行动”循环框架
+------------------------+
           ↑
+------------------------+
|       BaseAgent        |  ← 提供通用执行流程、状态管理、SSE流式支持
+------------------------+
```

##### 1. `BaseAgent`（基础层）

- 定义智能体的通用生命周期与执行流程，支持同步（`run`）与流式（`runStream`）两种执行模式。
- 内置状态机（`IDLE/RUNNING/FINISHED/ERROR`），防止非法状态迁移。
- 支持最大步骤限制（`maxSteps`）、会话上下文管理（`messageList`）和资源清理机制。

##### 2. `ReActAgent`（推理层）

- 基于 **ReAct（Reasoning & Acting）** 模式实现“思考-决策-行动”闭环。
- 抽象 `think()` 与 `act()` 方法，由子类实现具体推理与执行逻辑。
- 通过模板方法模式控制执行流程，确保一致性。

##### 3. `ToolCallAgent`（工具层）

- 实现大模型驱动的工具调用能力，自动解析 `ChatResponse` 中的 `ToolCall` 指令。
- 支持多工具异步执行、超时控制与失败熔断（`consecutiveFailures`）。
- 集成 `SessionContextManager`，基于 Redis 持久化会话上下文，保障多轮对话连贯性。

##### 4. `MyAgent`（业务层）

- 继承自 `ToolCallAgent`，定制系统提示词（`SYSTEM_PROMPT`）与任务引导逻辑（`nextStepPrompt`）。
- 注入 `ChatClient` 与可用工具列表，实现具体业务场景下的智能行为。

#### 执行流程

```
用户输入 → Agent.run() / runStream()
         ↓
   状态校验（validateBeforeRun）
         ↓
   进入 step() 循环（≤ maxSteps）
         ↓
     think() → 大模型判断是否调用工具
         ↓
      act()  → 执行工具调用并更新上下文
         ↓
   结果通过 SSE 或同步返回前端
         ↓
  达到 FINISHED 或 maxSteps → 结束
```

### 2.RAG检索流程

用户提问 → 查询改写 → 确定策略名 → 工厂创建 Advisor → 构建 Retriever → 执行向量检索 → 注入上下文 → 大模型生成

```
用户输入问题
      ↓
[1] 查询改写（Query Rewriting）
      ↓
[2] 确定检索策略（Strategy Selection）
      ↓
[3] 构建上下文处理器（ContextualQueryAugmenter）
      ↓
[4] 获取 RAG Advisor——含检索逻辑
      ↓
[5] 执行流式对话：检索 + 增强 + 生成
      ↓
[6] 流式返回结果（Flux<String>）
```

##### 1.查询改写（Query Rewriting）

```
String rewrittenQuery = queryRewriter.doQueryRewrite(message);
```

- **作用**：对用户原始输入进行语义优化，提升后续检索的准确性。
- **实现**：使用 `QueryRewriter`，基于大模型（LLM）将口语化、模糊或不完整的查询改写为更正式、适合检索的表达。
- 示例:
  - 原始：`"AI咋调用工具？"`
  - 改写后：`"人工智能智能体如何调用外部工具进行任务处理？"`

> ✅ 目的：提升召回率（Recall），让检索更精准。

##### 2.确定检索策略（Strategy Selection）

```
String actualStrategy = (strategyName != null && !strategyName.isEmpty())
        ? strategyName
        : defaultStrategy;
```

- **作用**：支持动态选择不同的知识库作为检索源。
- 来源：
  - 由调用方传入 `strategyName`（如 `"local-pgvector"`、`"aliyun-opensearch"` 等）
  - 若未指定，则使用配置文件中的默认策略 `${rag.default-strategy:local-pgvector}`

> ✅ 优势：支持多数据源、多场景、可扩展。

| 策略名称           | 实现类                  | 检索方式                                    | 匹配逻辑                 |
| ------------------ | ----------------------- | ------------------------------------------- | ------------------------ |
| `local-pgvector`   | `LocalPgVectorStrategy` | **本地向量数据库（PostgreSQL + pgvector）** | 向量相似度 + 元数据过滤  |
| `aliyun-dashscope` | `AliyunStrategy`        | **阿里云通义千问知识库服务**                | 向量语义检索（平台封装） |

##### 3.**构建上下文处理器（ContextualQueryAugmenter）**

```
ContextualQueryAugmenter augmenter = new ContextualQueryAugmenterFactory.Builder()
        .allowEmptyContext(false)
        .emptyContextPromptTemplate(new PromptTemplate("抱歉，未找到相关知识库信息..."))
        .build();
```

- **作用**：控制当检索结果为空时的行为。
- 关键配置：
  - `allowEmptyContext(false)`：不允许空上下文继续生成 → 触发降级提示
  - `emptyContextPromptTemplate(...)`：自定义无结果时的回复内容

> ✅ 优势：避免“幻觉式回答”，增强可控性与用户体验。

##### 4.获取 RAG 顾问（Advisor）

```
Advisor ragAdvisor = ragAdvisorFactory.createRagAdvisor(actualStrategy, augmenter);
```

- **作用**：根据 `strategyName` 动态创建对应的 RAG 检索逻辑。
- 背后逻辑：
  - `RagAdvisorFactory` 根据策略名（如 `local-pgvector`）返回不同的 `Advisor` 实现
  - 每个Advisor封装了：
    - 向量数据库连接（如 PGVector）
    - 检索器（Retriever）
    - 上下文拼接逻辑
    - 提示词模板（PromptTemplate）

> ✅ 设计亮点：**策略模式 + 工厂模式**，实现多源解耦。

##### 5.执行 RAG 流式对话

```
baseChatClient.prompt()
    .user(rewrittenQuery)
    .advisors(spec -> spec.param(CONVERSATION_ID, sessionId))
    .advisors(ragAdvisor)
    .stream()
    .content()
```

- 执行流程：
  1. 将改写后的查询发送给大模型
  2. advisors(ragAdvisor)触发检索动作：
     - 从向量库中检索最相关的文档片段
     - 将文档内容注入 prompt 上下文
  3. 大模型结合检索结果生成回答
  4. 使用 `.stream()` 实现 **逐字流式输出**

> ✅ 效果：用户无需等待整个检索+生成完成即可看到回复。

##### 6.绑定专用线程池（资源隔离）

```
subscribeOn(Schedulers.fromExecutor(ragQueryExecutor))
```

- **作用**：将 RAG 查询任务提交到专用线程池 `ragQueryExecutor`
- 优势：
  - 防止阻塞主 Web 线程池（Tomcat）
  - 控制并发量，避免数据库或向量库压力过大
  - 提升系统稳定性

### 3.RAG知识库构建

#### 1.分词器

基于 **Token 数量** 的语义切片（非简单字符分割）

```java
    public List<Document> splitCustomized(List<Document> documents) {
        // 初始化自定义配置的TokenTextSplitter，参数说明：
        // 1. chunkSize=200：每个切片最大Token数（控制切片长度）
        // 2. chunkOverlap=100：切片间重叠Token数（保留上下文关联，避免语义断裂）
        // 3. chunkNum=10：最大切片数量（单文档最多拆分为10个切片，防止过度拆分）
        // 4. maxChunkLength=5000：单文档最大处理Token数（过滤超长篇文档）
        // 5. trimWhitespace=true：自动去除切片前后空白字符（优化文本质量）
        TokenTextSplitter customizedSplitter = new TokenTextSplitter(200, 100, 10, 5000, true);
        // 执行自定义规则切片
        return customizedSplitter.apply(documents);
    }
```

#### 2.增强器

调用大模型为每个文档切片提取关键词

```java
    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(this.dashscopeChatModel, 5);
        return enricher.apply(documents);
    }
```



### 4. 工具组件

| 工具类 | 功能描述 | 主要方法 |
|--------|----------|----------|
| FileOperationTool | 文件读写工具 | readFile(读取文件)、writeFile(写入文件) |
| PDFGenerationTool | PDF生成工具 | generatePDF(生成带元数据和页码的PDF) |
| ResourceDownloadTool | 资源下载工具 | downloadResource(从URL下载文件) |
| WebSearchTool | 网络搜索工具 | （代码中未展示具体实现） |
| WebScrapingTool | 网页抓取工具 | （代码中未展示具体实现） |

### 5. 数据模型

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