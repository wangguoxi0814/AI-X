package com.aix.core.dto;

import java.util.Map;

public record RecordMessageRequest(
        String sessionId,
        String role,
        String content,
        String clientMessageId,
        Map<String, Object> metadata
) {
}
