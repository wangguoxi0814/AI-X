package com.aix.core.dto;

import java.util.List;
import java.util.Map;

public record CreateSessionRequest(
        String sessionId,
        String title,
        String source,
        List<String> tags,
        Map<String, Object> metadata
) {
}
