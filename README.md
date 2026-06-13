# AI-X 智能对话知识沉淀系统
基于完整采集用户和LLM对话数据，通过AI智能分析完整对话数据、对用户生成知识反馈报告，用于快速复盘和知识沉淀。

> **当前状态：** 设计阶段（文档已就绪，代码待实现）

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

## 模块结构（规划）

```
ai-x/
├── ai-x-mcp-server      # MCP 协议入口
├── ai-x-core            # 领域服务
├── ai-x-storage         # MyBatis-Plus（MySQL）
├── ai-x-rag             # Spring AI：Embedding、VectorStore、RAG Pipeline
├── ai-x-analysis        # 薄弱区、聚类、报告
├── ai-x-api             # REST 接口（含 Ingestion）
├── ai-x-ingest          # Cursor Hooks 脚本与安装模板（分发给集成方）
└── ai-x-common          # 公共工具与配置
```

---

## 快速开始

> 代码尚未实现，以下为预期启动流程。

**前置依赖：** JDK 21、MySQL 8.x、Milvus 2.x、Redis、Ollama（或 OpenAI API Key）

```bash
# 1. 启动基础设施（Docker Compose 待提供）
# MySQL + Milvus + Redis

# 2. 配置 application.yml
# spring.ai.model.embedding / spring.ai.vectorstore.milvus.host 等

# 3. 启动 AI-X 服务
mvn spring-boot:run -pl ai-x-api

# 4. 在业务项目中安装 Cursor Hooks（见 ai-x-ingest 文档）
```

---

## 文档

| 文档 | 内容 |
|------|------|
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
| M4 | 薄弱区报告分亏                                  |
| M5 | 可观测性、部署脚本、文档完善                           |

---

## License

待定
