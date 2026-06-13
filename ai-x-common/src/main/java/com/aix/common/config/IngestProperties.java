package com.aix.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aix.ingest")
public record IngestProperties(
        boolean enabled,
        String authToken,
        String bindAddress,
        int port
) {
    public IngestProperties() {
        this(true, "", "127.0.0.1", 8080);
    }
}
