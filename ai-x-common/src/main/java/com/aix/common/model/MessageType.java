package com.aix.common.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对话消息类型，与 {@code chat_message.message_type} 及 Hook ingest API 的 {@code messageType} 对齐。
 */
public enum MessageType {

    UNKNOWN(0),
    USER(1),
    ASSISTANT(2),
    THOUGHT(3),
    SYSTEM(4);

    private static final Map<Integer, MessageType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(MessageType::getCode, Function.identity()));

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageType fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }
}
