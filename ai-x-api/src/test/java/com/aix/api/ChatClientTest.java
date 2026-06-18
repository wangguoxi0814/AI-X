package com.aix.api;

import com.aix.api.support.ChatClientLiveTestApplication;
import com.aix.api.support.ChatClientTestApplication;
import com.aix.rag.config.AIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ChatClient 测试：单元测试不依赖外部服务；集成测试验证 Spring 容器中的 Bean 装配。
 */
class ChatClientTest {

    /**
     * 方式一（推荐）：纯单元测试，不启动 Spring、不调真实 LLM。
     * 与 {@link AIConfig} 中 defaultSystem 配置保持一致，验证 prompt 链路与响应解析。
     */
    @Test
    void chatClient_repliesWhenPrompted() {
        ChatModel chatModel = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage("你好！有什么可以帮你的？")))
        );

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是知识问答助手
                        """)
                .build();

        String content = chatClient.prompt()
                .user("你好！")
                .call()
                .content();

        assertThat(content).isNotBlank().contains("你好");
    }

    /**
     * 方式二：Spring 集成测试，Mock ChatModel 避免真实 API 调用与费用。
     * 使用 {@link ChatClientTestApplication} 最小上下文，不依赖 MySQL / Milvus / API Key。
     */
    @Nested
    @SpringBootTest(classes = ChatClientTestApplication.class)
    class SpringIntegrationTest {

        @MockBean
        private ChatModel chatModel;

        @Autowired
        private ChatClient chatClient;

        @BeforeEach
        void stubChatModel() {
            when(chatModel.call(any(Prompt.class)))
                    .thenReturn(new ChatResponse(
                            List.of(new Generation(new AssistantMessage("你好！有什么可以帮你的？")))
                    ));
        }

        @Test
        void injectedChatClient_repliesWhenPrompted() {
            String content = chatClient.prompt()
                    .user("你好！")
                    .call()
                    .content();

            assertThat(content).isNotBlank().contains("你好");
        }
    }

    /**
     * 方式三：真实 LLM 联调（手动执行）。
     * 需设置环境变量 DASHSCOPE_API_KEY；会走 DashScope OpenAI 兼容接口，仍不依赖 MySQL。
     */
    @Nested
    @SpringBootTest(classes = ChatClientLiveTestApplication.class)
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    class LiveIntegrationTest {

        @Autowired
        private ChatClient chatClient;

        @Test
        void chatClient_callsRealLlm() {
            String content = chatClient.prompt()
                    .user("用一句话介绍 Spring AI 的 ChatClient。")
                    .call()
                    .content();

            assertThat(content).isNotBlank();
            System.out.println(content);
        }
    }
}
