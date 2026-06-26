package com.aix.core.service.chat;

import reactor.core.publisher.Flux;

public interface ChatService {

    Flux<String> chat(String question);
}
