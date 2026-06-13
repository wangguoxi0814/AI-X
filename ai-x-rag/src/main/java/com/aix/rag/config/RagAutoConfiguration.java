package com.aix.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI RAG 配置占位。
 * M2 启用：EmbeddingModel、Milvus VectorStore、RetrievalAugmentationAdvisor。
 */
@Configuration
@ConditionalOnProperty(prefix = "aix.rag", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RagAutoConfiguration {
}
