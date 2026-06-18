package com.aix.api.support;

import com.aix.rag.config.AIConfig;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * ChatClient 真实 LLM 联调专用上下文：启用 OpenAI/DashScope ChatModel，但不依赖 MySQL / Milvus。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        MilvusVectorStoreAutoConfiguration.class
})
@Import(AIConfig.class)
public class ChatClientLiveTestApplication {
}
