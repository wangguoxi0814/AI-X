CREATE TABLE IF NOT EXISTS aix_session (
    session_id   VARCHAR(64)  PRIMARY KEY,
    title        VARCHAR(512),
    source       VARCHAR(32)  NOT NULL DEFAULT 'cursor',
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    metadata_json JSON,
    started_at   DATETIME(3)  NOT NULL,
    ended_at     DATETIME(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS aix_message (
    message_id        VARCHAR(64)  PRIMARY KEY,
    session_id        VARCHAR(64)  NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    content           MEDIUMTEXT   NOT NULL,
    seq               INT          NOT NULL,
    client_message_id VARCHAR(128) NOT NULL,
    metadata_json     JSON,
    created_at        DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_session_client_msg (session_id, client_message_id),
    KEY idx_session_seq (session_id, seq),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES aix_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
