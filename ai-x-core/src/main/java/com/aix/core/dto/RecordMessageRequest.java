package com.aix.core.dto;

import java.util.Map;

public record RecordMessageRequest(
        String sessionId,
        String role,
        String content,
        String messageId,
        Integer event,
        Map<String, Object> metadata
) {
}
