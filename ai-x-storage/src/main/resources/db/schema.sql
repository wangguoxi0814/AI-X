CREATE DATABASE IF NOT EXISTS aix
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE aix;

CREATE TABLE IF NOT EXISTS chat_session (
    id               BIGINT        NOT NULL COMMENT '主键ID（雪花ID）',
    session_id       VARCHAR(64)   NOT NULL COMMENT '会话业务ID',
    title            VARCHAR(512)           COMMENT '会话标题',
    source           VARCHAR(32)            DEFAULT NULL COMMENT '数据来源',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id                BIGINT        NOT NULL COMMENT '主键ID（雪花ID）',
    message_id        VARCHAR(128)  NOT NULL COMMENT '客户端消息ID',
    session_id        VARCHAR(64)   NOT NULL COMMENT '所属会话ID',
    role              VARCHAR(16)   NOT NULL COMMENT '角色：user/assistant/system',
    content           MEDIUMTEXT    NOT NULL COMMENT '消息正文',
    seq               INT           NOT NULL COMMENT '会话内顺序号，从 1 递增',
    event             INT           NOT NULL COMMENT 'Hook 事件码',
    metadata_json     JSON                   COMMENT '扩展元数据 JSON',
    deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    create_user_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '创建人ID',
    create_time       DATETIME(3)   NOT NULL COMMENT '创建时间',
    update_user_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '更新人ID',
    update_time       DATETIME(3)   NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_message_event (session_id, message_id, event),
    KEY idx_session_seq (session_id, seq),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';
