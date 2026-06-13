package com.aix.core.dto;

import com.aix.common.model.ApiCode;

public record IngestResult(
        ApiCode code,
        String messageId,
        String sessionId,
        boolean duplicate
) {
}
