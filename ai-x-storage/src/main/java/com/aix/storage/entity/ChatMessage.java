package com.aix.storage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    @TableField("message_id")
    private String messageId;

    private String sessionId;

    private String role;

    private String content;

    private Integer seq;

    @TableField("client_message_id")
    private String clientMessageId;

    @TableField("metadata_json")
    private String metadataJson;
}
