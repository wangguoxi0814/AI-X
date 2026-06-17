package com.aix.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                你是一个会话摘要助手，根据用户给定内容，生成知识反馈帮助用户知识沉淀
                """)
                .build();
    }

}
