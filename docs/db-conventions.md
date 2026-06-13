# AI-X 数据库规范

> 版本：v1.4  
> 日期：2026-06-13  
> 关联：[dev-conventions.md](./dev-conventions.md) §3.2

本文档约定 MySQL 表结构、审计字段、注释与 MyBatis-Plus 映射规则。**所有新建与变更表必须遵守。**

---

## 1. 适用范围

- 数据库：MySQL 8.x
- DDL 位置：`ai-x-storage/src/main/resources/db/`
- 实体位置：`com.aix.storage.entity`
- 逻辑删除、审计字段填充：MyBatis-Plus 全局配置 + `BaseEntity`

---

## 2. 主键 `id`（每张业务表必有）

每张业务表第一列必须是 **雪花主键 `id`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT NOT NULL` | 内部主键，Java 类型 `Long`；**应用层生成**，禁止 `AUTO_INCREMENT` |
| 生成策略 | MyBatis-Plus `IdType.ASSIGN_ID` | 内置 Snowflake 雪花算法 |

**业务唯一键**（如 `session_id`、`message_id`）与 `id` 并存：

- `id`：库内关联、分页、排序、Join 优先使用
- `{业务}_id`：对外 API、Cursor 对话 ID、幂等键等 **业务标识**，加 `UNIQUE KEY`

**禁止：**

- 数据库 `AUTO_INCREMENT` 生成 `id`
- 仅用 VARCHAR 业务键作主键而不设 `id`
- 在子实体重复声明 `id`（统一继承 `BaseEntity`）

**分布式（可选）：** 多实例部署时在 `application.yml` 配置 MyBatis-Plus 雪花 `worker-id` / `datacenter-id`，避免 ID 冲突。

---

## 3. 强制审计字段（每张业务表必有）

除纯中间表、临时表外，**每一张业务表**必须包含以下 5 个字段，且顺序建议放在业务字段之后、表末尾：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `deleted` | `TINYINT(1) NOT NULL` | `0` | 逻辑删除：`0` 未删除，`1` 已删除 |
| `create_user_id` | `BIGINT NOT NULL` | `0` | 创建人 ID（`Long`）；`0` 表示系统默认 |
| `create_time` | `DATETIME(3) NOT NULL` | — | 创建时间 |
| `update_user_id` | `BIGINT NOT NULL` | `0` | 最后修改人 ID（`Long`）；`0` 表示系统默认 |
| `update_time` | `DATETIME(3) NOT NULL` | — | 最后修改时间 |

**禁止：**

- 用物理 `DELETE` 删除业务数据（应逻辑删除或归档）
- 省略上述任一字段
- 用 `created_at` / `updated_at` 等其它命名替代（全项目统一上表命名）

**说明：** 业务时间（如会话 `started_at`、`ended_at`）与审计时间（`create_time` / `update_time`）并存；前者表示领域含义，后者表示行生命周期。

---

## 4. 字段 COMMENT（强制）

### 4.1 规则

1. **每个字段**必须写 `COMMENT '...'`，不允许无注释列。
2. **每张表**必须写 `COMMENT='...'` 表级注释。
3. 注释使用 **中文**，简明说明业务含义、枚举取值（如有）。
4. 枚举字段在 COMMENT 中列出可选值，例如：`状态：active-进行中 ended-已结束`。

### 4.2 示例

```sql
CREATE TABLE IF NOT EXISTS chat_example (
    id              BIGINT        NOT NULL COMMENT '主键ID（雪花ID）',
    name            VARCHAR(128)  NOT NULL COMMENT '名称',
    deleted         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    create_user_id  BIGINT        NOT NULL DEFAULT 0 COMMENT '创建人ID',
    create_time     DATETIME(3)   NOT NULL COMMENT '创建时间',
    update_user_id  BIGINT        NOT NULL DEFAULT 0 COMMENT '更新人ID',
    update_time     DATETIME(3)   NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例表';
```

---

## 5. 命名规范

### 5.1 表名：业务域前缀 + 蛇形

- 表名使用 **小写蛇形**（snake_case），**不要求**统一 `aix_` 前缀。
- 使用 **业务域前缀** 区分不同子系统/模块；**同一业务域内前缀必须一致**。
- 表名应能直接看出所属领域，避免跨域混用同一前缀。

**本项目业务域前缀（约定）：**

| 业务域 | 表前缀 | 说明 | 示例 |
|--------|--------|------|------|
| 对话采集 | `chat_` | 会话、消息、附件（规划） | `chat_session`、`chat_message` |
| 知识库 | `kb_` | 知识条目、分类（规划） | `kb_entry` |
| 分析 | `ana_` | 薄弱区报告、聚类快照（规划） | `ana_weak_area_report` |
| 系统 | `sys_` | 用户、配置（规划） | `sys_user` |

**禁止：**

- 无域前缀的泛化表名（如单独 `session`、`message`）易冲突
- 同一领域内混用不同前缀（如 `chat_session` 与 `aix_message` 并存）
- 为了「项目名」强行所有表加 `aix_` 而无领域含义

### 5.2 实体类名：与表名严格对应

**规则：** Java 实体类名 = 表名的 **PascalCase（大驼峰）**，一一对应，**禁止**再加 `Entity` 等后缀。

| 表名 | 实体类 | `@TableName` |
|------|--------|--------------|
| `chat_session` | `ChatSession` | `@TableName("chat_session")` |
| `chat_message` | `ChatMessage` | `@TableName("chat_message")` |
| `kb_entry` | `KbEntry` | `@TableName("kb_entry")` |

转换：`{domain}_{name}` → 去掉下划线后每段首字母大写并拼接。

**Mapper 命名：** `{实体类名}Mapper`，如 `ChatSessionMapper`、`ChatMessageMapper`。

**禁止：**

- 表 `chat_session` 对应类 `SessionEntity`（与表名不对应）
- 实体类名与 `@TableName` 不一致
- 一个实体映射多张表

### 5.3 其它命名

| 对象 | 规范 | 示例 |
|------|------|------|
| 字段名 | 蛇形小写 | `client_message_id` |
| 主键 | `id` BIGINT 雪花ID | 内部主键，`IdType.ASSIGN_ID` |
| 业务唯一键 | `{业务}_id` + `UNIQUE KEY` | `session_id`、`message_id` |
| 布尔/标志 | `TINYINT(1)` | `deleted` |
| 时间 | `DATETIME(3)` | 毫秒精度 |
| 大文本 | `TEXT` / `MEDIUMTEXT` / `LONGTEXT` | 对话正文 |
| JSON | `JSON` 类型 | `metadata_json` |
| Java 实体基类 | 继承 `BaseEntity` | 审计字段不在子类重复声明 |

---

## 6. 实体与 MyBatis-Plus

### 6.1 继承 BaseEntity

所有业务实体 **必须** 继承 `com.aix.storage.entity.BaseEntity`，不得在各实体重复定义审计字段。

```java
@TableName("chat_message")
public class ChatMessage extends BaseEntity {
    // 仅声明本表业务字段
}
```

### 6.2 BaseEntity 字段映射

| 数据库列 | Java 属性 | 说明 |
|----------|-----------|------|
| `id` | `id`（`Long`） | `@TableId(type = IdType.ASSIGN_ID)` 雪花主键 |
| `deleted` | `deleted` | `@TableLogic` 逻辑删除 |
| `create_user_id` | `createUserId`（`Long`） | `@TableField(fill = INSERT)` |
| `create_time` | `createTime` | `@TableField(fill = INSERT)` |
| `update_user_id` | `updateUserId`（`Long`） | `@TableField(fill = INSERT_UPDATE)` |
| `update_time` | `updateTime` | `@TableField(fill = INSERT_UPDATE)` |

审计字段由 `AuditMetaObjectHandler` 自动填充，Service 层 **无需** 手动 `setCreateTime`（除非特殊覆盖）。

### 6.3 逻辑删除

- 配置见 `application.yml` 中 `mybatis-plus.global-config.db-config.logic-delete-*`
- 查询默认过滤 `deleted = 1` 的行
- 删除操作使用 `mapper.deleteById` / 逻辑删除 API，禁止手写 `DELETE FROM`

### 6.4 当前操作人

v1 默认操作人：`0L`（常量 `AuditConstants.SYSTEM_USER_ID`）。  
多用户阶段从上下文（ThreadLocal / Security）读取真实用户 `Long` 型 ID 注入 Handler。

---

## 7. 索引与外键

| 规则 | 说明 |
|------|------|
| 主键 | 每张表必须有主键 |
| 唯一约束 | 幂等键、业务唯一键显式 `UNIQUE KEY`，COMMENT 说明用途 |
| 外键 | 按需使用；命名 `fk_{从表}_{主表}` |
| 索引命名 | `uk_` 唯一索引，`idx_` 普通索引 |

---

## 8. 新增 / 变更表流程

1. 在 `db/schema.sql` 或版本化迁移脚本编写 DDL（含 `id`、审计字段、全部 COMMENT）
2. Code Review 对照 **§2 id**、**§3 审计字段**、**§4 COMMENT** 检查
3. 新增实体类 `{TablePascalCase} extends BaseEntity`（类名与表名对应，无 `Entity` 后缀）
4. 新增 `{Entity}Mapper extends BaseMapper<{Entity}>`
5. 更新本文档 **§9 表清单**（如有新表）
6. 禁止在 `ai-x-core` 或其它模块定义 Entity / Mapper

---

## 9. 当前表清单

| 表名 | 实体类 | 业务域 | 说明 |
|------|--------|--------|------|
| `chat_session` | `ChatSession` | 对话采集 | AI 对话会话 |
| `chat_message` | `ChatMessage` | 对话采集 | 会话消息（user/assistant） |

---

## 10. Code Review 检查清单

- [ ] 是否包含雪花主键 `id`（BIGINT，非 AUTO_INCREMENT）
- [ ] 是否包含 `deleted`、`create_user_id`、`create_time`、`update_user_id`、`update_time`
- [ ] 每个字段是否有 `COMMENT`
- [ ] 表是否有 `COMMENT='...'`
- [ ] 表名是否使用正确的 **业务域前缀**（同域一致）
- [ ] 实体类名是否为表名的 PascalCase，且 **无 `Entity` 后缀**
- [ ] `@TableName` 是否与表名完全一致
- [ ] 实体是否继承 `BaseEntity`
- [ ] Mapper 是否仅在 `ai-x-storage`
- [ ] 是否误用物理删除

---

## 11. 相关文档

- [dev-conventions.md](./dev-conventions.md) — 模块职责与代码位置
- [requirements.md](./requirements.md) — 数据模型需求

---

*表结构变更时请同步更新 `schema.sql` 与本节表清单。*
