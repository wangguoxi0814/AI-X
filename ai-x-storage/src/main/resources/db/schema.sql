USE AIX;

CREATE TABLE IF NOT EXISTS chat_session (
    id               BIGINT        NOT NULL COMMENT '主键ID（雪花ID）',
    session_id       VARCHAR(64)   NOT NULL COMMENT '会话ID，与 Cursor conversation_id 对齐',
    title            VARCHAR(512)           COMMENT '会话标题',
    source           VARCHAR(32)   NOT NULL DEFAULT 'cursor' COMMENT '来源：cursor/cli/api 等',
    status           VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态：active-进行中 ended-已结束',
    metadata_json    JSON                   COMMENT '扩展元数据 JSON',
    started_at       DATETIME(3)   NOT NULL COMMENT '会话开始时间（业务字段）',
    ended_at         DATETIME(3)            COMMENT '会话结束时间（业务字段）',
    deleted          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    create_user_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '创建人ID',
    create_time      DATETIME(3)   NOT NULL COMMENT '创建时间',
    update_user_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '更新人ID',
    update_time      DATETIME(3)   NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id                BIGINT        NOT NULL COMMENT '主键ID（雪花ID）',
    message_id        VARCHAR(64)   NOT NULL COMMENT '消息业务ID',
    session_id        VARCHAR(64)   NOT NULL COMMENT '所属会话ID',
    role              VARCHAR(16)   NOT NULL COMMENT '角色：user/assistant/system',
    content           MEDIUMTEXT    NOT NULL COMMENT '消息正文',
    seq               INT           NOT NULL COMMENT '会话内顺序号，从 1 递增',
    client_message_id VARCHAR(128)  NOT NULL COMMENT '客户端幂等键',
    metadata_json     JSON                   COMMENT '扩展元数据 JSON',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    create_user_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '创建人ID',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    update_user_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '更新人ID',
    update_time       DATETIME(3)   NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id),
    UNIQUE KEY uk_session_client_msg (session_id, client_message_id),
    KEY idx_session_seq (session_id, seq),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';
