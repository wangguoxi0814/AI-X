# AI-X 开发基本规范

> 版本：v1.0  
> 日期：2026-06-13  
> 适用范围：AI-X 多模块 Java 工程及 `ai-x-ingest` 集成模板

本文档约定 **代码放哪里、模块谁依赖谁、哪些写法禁止**，避免后续开发时职责混乱、循环依赖和重复实现。

---

## 1. 总则

1. **单一职责**：一个模块只解决一类问题；不要把 Mapper、REST、MCP、RAG 混在同一模块。
2. **依赖单向**：只允许上层依赖下层，禁止反向依赖与循环依赖（见 §2）。
3. **入口薄、领域厚**：`ai-x-api`、`ai-x-mcp-server` 只做协议适配与参数校验，业务逻辑放在 `ai-x-core`（及 `ai-x-analysis`、`ai-x-rag`）。
4. **数据访问收敛**：所有 MyBatis `Mapper`、数据库实体、`schema.sql` 统一放在 `ai-x-storage`，其他模块 **不得** 新建 Mapper。
5. **可复用才下沉**：仅当 ≥2 个模块需要时，才提取到 `ai-x-common`；否则留在本模块。

---

## 2. 模块依赖关系

```
                    ┌─────────────┐
                    │  ai-x-api   │  ← 唯一 Spring Boot 启动模块
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
   ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
   │ai-x-mcp-server│ │ai-x-analysis│ │  (直接依赖)  │
   └──────┬───────┘ └──────┬──────┘ └──────┬───────┘
          │                │               │
          └────────────────┼───────────────┘
                           ▼
                    ┌─────────────┐
                    │  ai-x-core  │  ← 领域服务、业务流程编排
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │ai-x-storage │ │  ai-x-rag   │ │ ai-x-common │
   └──────┬──────┘ └──────┬──────┘ └─────────────┘
          │               │
          └───────┬───────┘
                  ▼
           ┌─────────────┐
           │ ai-x-common │
           └─────────────┘

   ai-x-ingest/   ← 非 Maven 模块，不参与上述依赖
```

| 模块 | 可依赖 | 禁止依赖 |
|------|--------|----------|
| `ai-x-common` | 无（仅第三方基础库） | 任何 `com.aix.*` 模块 |
| `ai-x-storage` | `ai-x-common` | `core` / `api` / `rag` / `analysis` / `mcp-server` |
| `ai-x-rag` | `ai-x-common` | `core` / `api` / `storage`（RAG 不直接访问 DB） |
| `ai-x-core` | `common`、`storage`、`rag` | `api`、`mcp-server`、`analysis` |
| `ai-x-analysis` | `core`（传递依赖 storage/rag/common） | `api`、`mcp-server` |
| `ai-x-mcp-server` | `core` | `api`、`analysis` |
| `ai-x-api` | `core`、`analysis`、`mcp-server` | 禁止业务模块依赖 `api` |

---

## 3. 各模块职责与代码位置

### 3.1 `ai-x-common` — 跨模块公共定义

**职责：** 与具体业务无关、被多个模块共用的类型与配置绑定。

**包路径：** `com.aix.common.*`

| 子包 | 存放内容 | 示例 |
|------|----------|------|
| `common.model` | 通用枚举、统一响应包装 | `ApiCode`、`ApiResponse` |
| `common.exception` | 业务异常基类 | `AixException` |
| `common.config` | 跨模块 `@ConfigurationProperties` | `IngestProperties` |
| `common.util` | 无状态工具类 | 字符串/时间/ID 工具（按需新增） |

**禁止存放：**

- Entity、Mapper、Service、Controller
- 仅单一模块使用的 DTO
- Spring AI、MCP、MyBatis 相关代码

---

### 3.2 `ai-x-storage` — 持久层（MySQL）

**职责：** 数据库表结构、实体映射、数据访问接口。**全项目唯一的 Mapper 归属模块。**

**包路径：** `com.aix.storage.*`

| 子包 / 路径 | 存放内容 | 示例 |
|-------------|----------|------|
| `storage.entity` | MyBatis-Plus 实体，与表一一对应 | `SessionEntity`、`MessageEntity` |
| `storage.mapper` | `BaseMapper` 接口 | `SessionMapper`、`MessageMapper` |
| `resources/db/` | DDL、`schema.sql`、迁移脚本 | `schema.sql`、`V1__*.sql`（若引入 Flyway） |
| `resources/mapper/` | 复杂 SQL 的 XML（按需） | `MessageMapper.xml` |

**命名约定：**

- 表名：`aix_` 前缀 + 蛇形，如 `aix_session`、`aix_message`
- 实体：`XxxEntity`，禁止 `@TableName` 以外的表名硬编码在 Service 中
- Mapper：`XxxMapper`，与实体同名

**禁止存放：**

- `@Service` 业务逻辑
- REST / MCP 接口
- 事务编排（放在 `ai-x-core`）
- 向量库（Milvus）访问代码

**新增表流程：**

1. 在 `resources/db/schema.sql`（或迁移脚本）增加 DDL  
2. 在 `storage.entity` 新增 `XxxEntity`  
3. 在 `storage.mapper` 新增 `XxxMapper`  
4. 在 `ai-x-core` 的 Service 中注入 Mapper 使用  

---

### 3.3 `ai-x-rag` — Spring AI / RAG

**职责：** Embedding、VectorStore（Milvus）、Document 切分、RAG Pipeline、与 LLM 相关的 **技术能力封装**（不含业务规则）。

**包路径：** `com.aix.rag.*`

| 子包 | 存放内容 | 示例 |
|------|----------|------|
| `rag.config` | Spring AI、`VectorStore` Bean 配置 | `RagAutoConfiguration` |
| `rag.embedding` | 向量化封装 | `MessageEmbeddingService`（待建） |
| `rag.retrieval` | 检索、Rerank、Advisor | `KnowledgeRetriever`（待建） |
| `rag.pipeline` | Ingest / Query 流水线 | `RagIngestPipeline`（待建） |

**禁止存放：**

- MySQL Entity / Mapper
- HTTP Controller、MCP Tool 实现
- 薄弱区报告等领域分析逻辑（属于 `ai-x-analysis`）
- 会话/message 写入的主业务流程（属于 `ai-x-core`，可调用本模块能力）

**调用关系：** `ai-x-core` 编排业务 → 调用 `ai-x-rag` 提供的接口完成向量化和检索。

---

### 3.4 `ai-x-core` — 领域服务层

**职责：** 核心业务规则、事务边界、多存储编排（MySQL + 向量 + 队列）。**项目的「领域心脏」。**

**包路径：** `com.aix.core.*`

| 子包 | 存放内容 | 示例 |
|------|----------|------|
| `core.service` | 领域服务接口 + 实现 | `ChatRecordService`、`ChatRecordServiceImpl` |
| `core.dto` | 领域层入参/出参（与传输协议无关） | `CreateSessionRequest`、`IngestResult` |
| `core.config` | 本模块 Spring 配置 | `CoreConfiguration` |
| `core.event` | 领域事件（按需） | `MessageRecordedEvent` |
| `core.converter` | Entity ↔ DTO 转换（按需） | `MessageConverter` |

**允许：**

- 注入 `com.aix.storage.mapper.*`（当前做法）
- 注入 `ai-x-rag` 提供的服务接口
- `@Transactional` 事务

**禁止存放：**

- 新建 Mapper / Entity
- `@RestController`、`@McpTool` 等对外入口
- `application.yml`（放在 `ai-x-api`）
- 直接操作 Milvus SDK（应经 `ai-x-rag`）

**Service 命名：** 接口 `XxxService`，实现 `XxxServiceImpl`，放在同一 `service` 包。

---

### 3.5 `ai-x-analysis` — 分析与报告

**职责：** 薄弱区识别、主题聚类、统计报告生成等 **离线/按需分析** 能力。

**包路径：** `com.aix.analysis.*`

| 子包 | 存放内容 | 示例 |
|------|----------|------|
| `analysis.service` | 分析服务 | `WeakAreaAnalysisService`（待建） |
| `analysis.model` | 报告、簇等领域模型 | `WeakAreaReport`（待建） |
| `analysis.job` | 定时批任务 | `DailyAnalysisJob`（待建） |

**禁止存放：**

- Mapper、Entity
- REST / MCP 入口（由 `api` / `mcp-server` 调用本模块 Service）
- 通用 RAG 检索封装（属于 `ai-x-rag`）

---

### 3.6 `ai-x-mcp-server` — MCP 协议入口

**职责：** MCP Tools / Resources / Prompts 注册与实现，将 MCP 调用 **委托** 给 `ai-x-core`（及 `ai-x-analysis`）。

**包路径：** `com.aix.mcp.*`

| 子包 | 存放内容 | 示例 |
|------|----------|------|
| `mcp.tool` | MCP Tool 实现 | `RecordMessageTool`（待建） |
| `mcp.resource` | MCP Resource 提供者 | `SessionResource`（待建） |
| `mcp.prompt` | MCP Prompt 模板 | `SummarizeSessionPrompt`（待建） |
| `mcp.config` | MCP Server 配置 | `McpServerConfiguration`（待建） |

**禁止存放：**

- 复杂业务逻辑（一行以上应下沉 `core`）
- Mapper、Entity
- Spring MVC Controller

**原则：** MCP Tool 方法 ≈ 参数解析 + 调用 `core` Service + 结果映射。

---

### 3.7 `ai-x-api` — HTTP 入口与启动

**职责：** Spring Boot 启动、REST API、Web 层横切（鉴权、全局异常、Actuator）。

**包路径：** `com.aix.api.*`

| 子包 / 路径 | 存放内容 | 示例 |
|-------------|----------|------|
| `api`（根） | 启动类 | `AiXApplication` |
| `api.controller` | 通用 REST | `HealthController` |
| `api.controller.ingest` | Ingestion REST | `IngestController` |
| `api.controller.admin` | 管理面 API（按需） | `AdminReindexController` |
| `api.dto` | **HTTP 专用** 请求/响应体（与 Controller 解耦时） | `StartSessionBody` |
| `api.config` | Filter、Interceptor、异常处理 | `IngestAuthFilter`、`GlobalExceptionHandler` |
| `resources/` | `application.yml`、环境配置 | 仅本模块 |

**禁止存放：**

- 业务 Service 实现（放在 `core`）
- Mapper、Entity
- MCP 协议代码
- Spring AI / Milvus 直接调用

**Controller 原则：**

- 只做：参数校验、`@Valid`、调用 `core` Service、HTTP 状态码映射
- `IngestController` 内嵌 `record` 可逐步迁到 `api.dto`，但 **不得** 迁入 `core`

---

### 3.8 `ai-x-ingest` — Cursor Hooks 集成模板

**职责：** 分发给 **业务项目** 的 Hook 脚本与安装说明，**不是 Maven 模块**，不参与 Java 编译。

```
ai-x-ingest/
├── README.md
└── template/.cursor/
    ├── hooks.json
    └── hooks/
        ├── aix_ingest.py
        ├── aix-ingest.ps1
        └── README.md
```

**禁止：**

- 在 AI-X 根目录放 `.cursor/` 用于服务端开发
- 在 Java 模块中引用 Hook 脚本路径

---

## 4. 横切关注点放哪里

| 关注点 | 归属模块 | 说明 |
|--------|----------|------|
| 统一错误码 | `ai-x-common` | `ApiCode` |
| 统一异常 | `ai-x-common` + `ai-x-api` | 异常类在 common；`@RestControllerAdvice` 在 api |
| Ingest Bearer 鉴权 | `ai-x-api` | `IngestAuthFilter` |
| 事务 | `ai-x-core` | Service 实现类方法上 |
| 异步 embedding 队列 | `ai-x-core` 触发，`ai-x-rag` 执行 | 不在 api 层 `@Async` |
| 配置项 `aix.*` | 绑定类放 `common` 或对应模块；**yaml 只在 api** | |
| 单元测试 | 与被测类 **同模块** `src/test/java` | 如 `ChatRecordServiceImplTest` 在 `ai-x-core` |

---

## 5. 新增功能决策树

```
需要持久化新数据？
  ├─ 是 → storage: Entity + Mapper + schema.sql
  │        core: Service 使用 Mapper
  └─ 否 → 跳过 storage

需要向量 / LLM？
  ├─ 是 → rag: 封装 Spring AI 能力
  │        core: 编排何时 embed / retrieve
  └─ 否 → 跳过 rag

需要对外暴露？
  ├─ HTTP → api: Controller + api.dto
  ├─ MCP  → mcp-server: Tool/Resource
  └─ 仅内部 → 仅 core / analysis Service

需要定时批处理？
  └─ analysis: job + service（api 可提供触发端点）
```

---

## 6. 典型场景示例

### 6.1 新增「知识条目」表

| 步骤 | 模块 | 文件 |
|------|------|------|
| 1 | `ai-x-storage` | `db/schema.sql` 增加 `aix_knowledge_entry` |
| 2 | `ai-x-storage` | `entity/KnowledgeEntryEntity.java` |
| 3 | `ai-x-storage` | `mapper/KnowledgeEntryMapper.java` |
| 4 | `ai-x-core` | `service/KnowledgeService.java` + `Impl` |
| 5 | `ai-x-rag` | 长文分块 + `VectorStore.add`（若需检索） |
| 6 | `ai-x-mcp-server` | `tool/AddKnowledgeTool.java` |
| 7 | `ai-x-api` | 可选管理 REST |

### 6.2 新增 MCP Tool `search_history`

| 步骤 | 模块 | 文件 |
|------|------|------|
| 1 | `ai-x-rag` | 检索 Pipeline |
| 2 | `ai-x-core` | `SearchService` 编排 rag + storage |
| 3 | `ai-x-mcp-server` | `tool/SearchHistoryTool.java` |

**错误做法：** 在 `mcp-server` 里写 Mapper 查询；在 `api` 里写 Milvus 调用。

---

## 7. 命名与代码风格

| 类型 | 规范 |
|------|------|
| Java 版本 | 21；优先 `record` 作不可变 DTO |
| 包名 | 全小写，`com.aix.{模块}.{层}` |
| 类名 | 大驼峰；Entity 后缀 `Entity`，Mapper 后缀 `Mapper` |
| 表字段 | 蛇形；Java 驼峰 + `@TableField` 映射 |
| 配置键 | `aix.{域}.{项}` 或 `spring.ai.*` |
| 日志 | SLF4J；关键路径带 `sessionId` / `messageId` |
| 注释 | 非显而易见业务规则才写；禁止无意义注释 |

---

## 8. 禁止清单（反模式）

| 反模式 | 正确做法 |
|--------|----------|
| 在 `ai-x-core` 新建 `XxxMapper` | 只在 `ai-x-storage` 新建 |
| 在 `ai-x-api` 写 `@Transactional` 业务 | 事务放在 `core` Service |
| 在 `ai-x-mcp-server` 直接 `sessionMapper.insert` | 调用 `ChatRecordService` |
| 在多个模块各写一套 Embedding 逻辑 | 统一 `ai-x-rag` |
| 把 Hook 脚本放进 `ai-x-api/src` | 放 `ai-x-ingest/template` |
| `ai-x-storage` 依赖 `ai-x-core` | 违反依赖方向，禁止 |
| Controller 内 50+ 行业务逻辑 | 下沉 `core` Service |
| Entity 暴露到 MCP/REST 响应 | 使用 DTO / `api.dto` |

---

## 9. 与需求文档的对应

| 需求能力 | 主要模块 |
|----------|----------|
| Cursor Hooks 采集 | `ai-x-ingest` + `ai-x-api`（Ingest REST）+ `ai-x-core` |
| MCP Tools | `ai-x-mcp-server` + `ai-x-core` |
| 对话持久化 | `ai-x-storage` + `ai-x-core` |
| 向量化 / RAG | `ai-x-rag` + `ai-x-core` |
| 薄弱区报告 | `ai-x-analysis` + `ai-x-rag` |
| 配置与启动 | `ai-x-api` |

---

## 10. 文档维护

- 新增 Maven 模块或调整依赖时，**必须同步更新本文档 §2、§3**。
- Code Review 以本文档为检查清单；不符合规范的 PR 应要求修正后再合并。

**相关文档：**

- [requirements.md](./requirements.md) — 功能与非功能需求
- [cursor-hooks-tech-options.md](./cursor-hooks-tech-options.md) — Hooks 采集方案
- [project_docs.md](./project_docs.md) — 技术沉淀

---

*本文档随项目结构演进维护；若与代码不一致，以仓库实际 `pom.xml` 依赖为准，并优先修正文档或代码使其一致。*
