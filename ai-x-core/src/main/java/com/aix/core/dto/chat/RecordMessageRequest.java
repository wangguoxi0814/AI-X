package com.aix.core.dto.chat;

import java.util.Map;

public record RecordMessageRequest(
        String sessionId,
        Integer messageType,
        String content,
        String messageId,
        Integer event,
        Map<String, Object> metadata
) {
}
