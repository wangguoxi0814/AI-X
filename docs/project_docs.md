# 项目技术沉淀（通用）

> 本文档记录项目演进中的通用技术要点，供后续类似系统参考。

---

## MCP 协议三要素

- **Tools（工具）**：MCP Server 向 AI 客户端注册的「可调用函数」。每个 Tool 有名称、参数 schema 和说明；AI 在对话中根据用户意图决定是否调用，客户端通过 MCP 协议转发请求到 Server 执行并返回结果。适合「写数据、查数据、触发动作」等副作用操作。
- **Resources（资源）**：只读的内容入口，通过 URI 标识（如 `session://xxx`）。Server 向客户端「列出可访问资源清单」，AI 或用户选中后读取其文本/Markdown 内容注入上下文；Resource 本身不执行业务逻辑、不产生写操作。适合「会话详情、知识条目正文、分析报告」等静态或快照型内容。
- **Tools 与 Resources 的分工**：需要参数化查询、写入、触发流程时用 Tools；已有明确 URI、整块读取即可的内容用 Resources。复杂场景可 Tool 返回结果，同时把持久化实体暴露为 Resource 供后续引用。
- **Prompts（提示模板）**：Server 预置的「标准任务话术」。客户端可列出 Prompt 列表，用户或 AI 选用某个 Prompt 时，Server 根据可选参数生成一段结构化 user message（可含 system 指引），注入当前对话。Prompt 不直接访问数据库或执行业务代码，而是 **规范 AI 该怎么想、怎么输出**；后续若需落库，仍配合 Tools 完成。

## MCP 服务化知识系统

- **MCP 标准 list 与 list_changed 成对出现**：`tools/list`、`resources/list`、`prompts/list` 为 Client 发起的 JSON-RPC 请求（有 id、有响应）；`notifications/tools/list_changed` 等为 Server 发起的单向通知（无 id、无响应）。Server 仅在 initialize 中声明 `listChanged: true` 时才应发送对应通知；Client 收到后应重新 list。二者均属 MCP 规范定义，非 Cursor 私有扩展。
- **采集模式**：对话数据宜采用「客户端主动上报 + 幂等键去重」模式，避免重复入库；用户消息与助手消息分角色存储，便于后续仅对用户提问做向量化以控制成本。
- **异步解耦**：消息持久化与 Embedding 向量化应解耦，写入路径保持低延迟，向量索引采用最终一致模型，并具备失败重试与断点续跑能力。

## 向量检索与知识库

- **双域知识模型**：个人上下文知识与通用技术知识应逻辑分离，在检索、分类、权限上采用不同策略；通用知识抽取需通过 LLM 或规则做「去上下文抽象」。
- **存储分离**：结构化业务数据（Session、Message、KnowledgeEntry）存 MySQL；语义向量存 Milvus，通过 `externalId` 与业务 ID 双向关联。不将向量塞入关系库，便于 RAG 独立调优。
- **AI 框架**：**Spring AI** 为唯一 AI / RAG 框架；Embedding、VectorStore（Milvus）、ChatClient、Document 切分、RAG Advisor 均通过 Spring AI 接入，不引入 LangChain4j。
- **RAG 编排**：`EmbeddingModel` 向量化 → `VectorStore` 检索 → `RetrievalAugmentationAdvisor` 注入上下文 → `ChatClient` 生成；检索类 MCP Tool（`search_history`、`search_knowledge`）走 Retrieve → [Rerank] → 返回片段或合成回答。
- **混合检索演进**：初期 Milvus 稠密向量检索；v1.1 引入 MySQL FULLTEXT 或 Elasticsearch BM25，与向量做 Hybrid Search，提升专有名词与短查询召回。
- **去重与合并**：知识入库前对向量相似度做阈值去重，避免重复条目；保留 sourceRef 实现从知识条目回溯原始对话。

## 薄弱区与学习分析

- **可解释信号**：薄弱区识别不应只输出结论，需附带可解释信号（重复提问、主题频次、知识覆盖率等），便于用户信任与人工校正。
- **聚类驱动主题发现**：对用户问题向量做主题聚类（K-Means、HDBSCAN 等），再在簇级别统计指标，比单纯关键词统计更能发现语义相近的盲区。
- **批分析与按需查询**：周期性批任务生成报告快照，同时通过 API/MCP 支持按需查询，平衡实时性与计算成本。

## Java 后端工程实践

- **运行时**：Java 21 LTS；Spring Boot 3.x + **Spring AI**。
- **模块化分层**：MCP 入口、领域服务、存储（MySQL）、RAG（`ai-x-rag` / Spring AI）、分析拆为独立模块；模型与向量库通过 Spring AI Starter 可插拔切换。
- **持久层约定**：使用 MyBatis-Plus 时，实体字段通过 `@TableField` 映射，框架默认驼峰转下划线；业务 ID 与 Milvus 中的 externalId 需双向关联。
- **可观测性基线**：结构化日志携带 traceId、sessionId；暴露写入 QPS、embedding 队列深度、RAG retrieve/rerank P95 等指标及健康检查端点。

## 安全与部署

- **本地优先**：对话与知识数据优先本地或私有部署，API Key 加密存储，可选敏感信息脱敏规则。
- **鉴权扩展**：MCP 连接层预留 token 或 mTLS 鉴权，为多用户与团队场景扩展做准备。

---

*最后更新：2026-06-13*
