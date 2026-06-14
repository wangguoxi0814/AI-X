package com.aix.storage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    @TableField("session_id")
    private String sessionId;

    private String title;

    private String source;

    private String status;

    @TableField("metadata_json")
    private String metadataJson;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
