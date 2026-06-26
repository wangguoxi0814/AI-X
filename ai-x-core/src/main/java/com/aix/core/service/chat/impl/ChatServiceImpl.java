package com.aix.core.service.chat.impl;

import com.aix.core.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService{

    private final ChatClient chatClient;

    @Override
    public Flux<String> chat(String question) {
        return chatClient.prompt(question)
                .stream()
                .content();
    }
}
