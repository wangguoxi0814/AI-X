# AI-X 智能对话知识沉淀系统

> **自动采集 · 智能分析 · 知识复盘**

AI-X 以**独立中间服务**，在**无嵌入、无侵入、无需人工干预、无额外Token消耗、无额外耗时**的前提下，稳定、完整地采集用户与 LLM 的对话数据。系统基于 Spring AI 构建对话知识库，自动生成**知识沉淀反馈报告**——帮你识别**知识薄弱区**、梳理**复杂任务的处理逻辑**，支撑快速复盘、经验沉淀与方法论总结。

> **当前状态：** M1 基础骨架已搭建（Ingestion REST + Hooks 模板），RAG / MCP 待实现

---

## 它能做什么

| 能力 | 说明                                       |
|------|------------------------------------------|
| **对话记录** | 完整保存 user / assistant 消息，支持幂等写入          |
| **语义检索** | 基于 Spring AI + Milvus 检索历史提问与知识条目        |
| **知识沉淀** | 个人知识与通用技术知识分域管理，支持自动抽取                   |
| **薄弱区分析** | 识别重复提问、主题频次异常等学习短板，快速复盘总结                |
| **Cursor Hooks 采集** | 集成方在自己项目中配置 Hook，对话自动上报 AI-X             |

---

## 客户端集成
| 客户端        | 说明                                | 进度  |
|------------|-----------------------------------|-----| 
| **Cursor** | 基于Cursor Hooks 采集用户和Cursor所有对话记录  | 进行中 |
| **其他客户端**  | 基于 Spring AI + Milvus 检索历史提问与知识条目 | 待扩展 |


--- 

## 架构概览

```
┌─────────────────┐                    ┌──────────────────────────────────────┐
│  Cursor / MCP   │  MCP / HTTP REST   │  AI-X（Spring Boot + Spring AI）     │
│  Client         │ ─────────────────► │  MCP Server · REST API · RAG Pipeline│
└─────────────────┘                    └───────────────┬──────────────────────┘
                                                       │
              ┌────────────────────────────────────────┼────────────────────────┐
              ▼                    ▼                   ▼                        ▼
         ┌─────────┐         ┌──────────┐       ┌──────────┐          ┌──────────────┐
         │  MySQL  │         │  Milvus  │       │  Redis   │          │ OpenAI/Ollama│
         │ 业务数据 │         │ 向量索引  │       │ 缓存/队列 │          │ 模型 API     │
         └─────────┘         └──────────┘       └──────────┘          └──────────────┘
```

**存储分工：** 结构化业务数据（Session、Message、Knowledge）存 **MySQL**；语义向量经 **Spring AI VectorStore** 写入 **Milvus**。

---

## 技术栈

| 层次 | 选型                                                               |
|------|------------------------------------------------------------------|
| 语言 | Java 21                                                          |
| 基础框架 | Spring Boot 3.x                                                  |
| AI / RAG | **Spring AI**（EmbeddingModel、VectorStore、ChatClient、RAG Advisor） |
| 协议 | MCP（Model Context Protocol）                                      |
| ORM | MyBatis-Plus                                                     |
| 关系库 | MySQL 8.x                                                        |
| 向量库 | Milvus 2.x                                                       |
| 缓存 / 队列 | Redis                                                            |
| 模型 | OpenAI / DeepSeek / Qwen / Ollama等(可配置)                          |
| 构建 | Maven                                                            |

---

## 两种集成方式

AI-X 是**独立运行的服务端**。开发者启动 AI-X 后，在自己公司的 Cursor 项目中接入，对话即自动沉淀到 AI-X。

### 1. Cursor Hooks（推荐 — 自动采集）

将 `ai-x-ingest` 提供的 Hook 模板复制到**业务项目**（非 AI-X 源码仓库）：

```
your-company-project/
├── .cursor/
│   ├── hooks.json
│   └── hooks/
│       ├── aix_ingest.py
│       └── aix-ingest.ps1
└── src/...
```

Hook 在 Agent 生命周期事件（`beforeSubmitPrompt`、`afterAgentResponse` 等）触发时，通过 HTTP 将对话 POST 到 AI-X REST 接口。采集失败 **fail-open**，不阻断 Cursor 对话。

环境变量：

| 变量 | 说明 |
|------|------|
| `AIX_API_BASE` | AI-X 地址，如 `http://127.0.0.1:8080` |
| `AIX_INGEST_TOKEN` | 鉴权 Token |

详见 [docs/cursor-hooks-tech-options.md](docs/cursor-hooks-tech-options.md)。

### 2. MCP Tools（检索与补录）

在 Cursor 中配置 AI-X MCP Server，使用 `record_message`、`search_history`、`search_knowledge` 等 Tool 进行检索、知识库操作与手动补录。

详见 [docs/requirements.md](docs/requirements.md) §4.1。

---

## 模块结构

```
AI-X/
├── pom.xml
├── ai-x-api/                  # Spring Boot 启动入口、Ingestion REST
├── ai-x-core/                 # ChatRecordService 等领域服务
├── ai-x-storage/              # MyBatis-Plus 实体、Mapper、schema.sql
├── ai-x-rag/                  # Spring AI 占位（M2）
├── ai-x-analysis/             # 薄弱区分析占位（M4）
├── ai-x-mcp-server/           # MCP Server 占位（M1）
├── ai-x-common/               # ApiResponse、异常、IngestProperties
├── ai-x-ingest/               # Cursor Hooks 模板（复制到业务项目）
│   └── template/.cursor/
└── docs/
```

---

## 快速开始

**前置依赖：** JDK 21、MySQL 8.x

```bash
# 1. 初始化数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS aix"
mysql -u root -p < ai-x-storage/src/main/resources/db/schema.sql

# 2. 编译
mvn compile -DskipTests

# 3. 启动（配置 MySQL 密码与 Ingest Token）
set AIX_MYSQL_PASSWORD=your_password
set AIX_INGEST_TOKEN=your-token
mvn spring-boot:run -pl ai-x-api
```

**安装 Cursor Hooks（在业务项目中）：**

```powershell
Copy-Item -Recurse -Force ai-x-ingest\template\.cursor .cursor
$env:AIX_API_BASE = "http://127.0.0.1:8080"
$env:AIX_INGEST_TOKEN = "your-token"
```

验证：`GET http://127.0.0.1:8080/api/health`

---

## 文档

| 文档 | 内容 |
|------|------|
| [docs/db-conventions.md](docs/db-conventions.md) | **数据库规范**：utf8mb4 字符集、审计字段、COMMENT、逻辑删除 |
| [docs/dev-conventions.md](docs/dev-conventions.md) | **开发规范**：模块职责、代码存放位置、依赖规则 |
| [docs/requirements.md](docs/requirements.md) | 功能需求、数据模型、Spring AI 架构、里程碑 |
| [docs/cursor-hooks-tech-options.md](docs/cursor-hooks-tech-options.md) | Cursor Hooks 采集方案、REST API、部署与联调 |
| [docs/project_docs.md](docs/project_docs.md) | MCP 协议、RAG、工程实践沉淀 |

---

## 里程碑

| 阶段 | 交付物                                      |
|------|------------------------------------------|
| M1 | 会话/消息入库 + Cursor Hooks 采集                |
| M2 | Spring AI Embedding + VectorStore + 语义检索 |
| M3 | 知识库 CRUD + 自动抽取                          |
| M4 | 薄弱区报告与分析                                  |
| M5 | 可观测性、部署脚本、文档完善                           |

---

## License

待定
