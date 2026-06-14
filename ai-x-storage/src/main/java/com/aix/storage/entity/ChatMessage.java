package com.aix.storage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    /** 客户端消息 ID */
    @TableField("message_id")
    private String messageId;

    private String sessionId;

    private String role;

    private String content;

    private Integer seq;

    /** Cursor Hook 事件码，见 {@link com.aix.common.model.CursorHookEvent} */
    private Integer event;

    @TableField("metadata_json")
    private String metadataJson;
}
