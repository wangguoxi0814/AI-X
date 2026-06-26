package com.aix.api.controller.chat;

import com.aix.core.dto.chat.ChatDTO;
import com.aix.core.service.chat.ChatRecordService;
import com.aix.core.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("stream")
    public Flux<String> chatStream(ChatDTO chatDTO) {
        return chatService.chat(chatDTO.question());
    }

}
