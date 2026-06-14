package com.aix.common.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对话消息类型，与 {@code chat_message.message_type} 及 Hook ingest API 的 {@code messageType} 对齐。
 */
public enum MessageType {

    UNKNOWN(0, "未知类型"),
    USER(1, "用户消息"),
    ASSISTANT(2, "助手回复"),
    THOUGHT(3, "Agent 思考"),
    SYSTEM(4, "系统消息");

    private static final Map<Integer, MessageType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(MessageType::getCode, Function.identity()));

    private final int code;
    private final String description;

    MessageType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static MessageType fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }
}
