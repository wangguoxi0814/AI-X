package com.aix.api.controller.ingest;

import com.aix.common.model.ApiCode;
import com.aix.core.dto.CreateSessionRequest;
import com.aix.core.dto.IngestResult;
import com.aix.core.dto.RecordMessageRequest;
import com.aix.core.service.ChatRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final ChatRecordService chatRecordService;

    public IngestController(ChatRecordService chatRecordService) {
        this.chatRecordService = chatRecordService;
    }

    @PostMapping("/sessions")
    public IngestResponse startSession(@Valid @RequestBody StartSessionBody body) {
        IngestResult result = chatRecordService.startSession(
                new CreateSessionRequest(
                        body.sessionId(),
                        body.title(),
                        body.source(),
                        body.tags(),
                        body.metadata()
                )
        );
        return toResponse(result);
    }

    @PostMapping("/messages")
    public IngestResponse recordMessage(@Valid @RequestBody RecordMessageBody body) {
        IngestResult result = chatRecordService.recordMessage(
                new RecordMessageRequest(
                        body.sessionId(),
                        body.role(),
                        body.content(),
                        body.clientMessageId(),
                        body.metadata()
                )
        );
        return toResponse(result);
    }

    @PostMapping("/sessions/{sessionId}/end")
    public IngestResponse endSession(@PathVariable String sessionId) {
        IngestResult result = chatRecordService.endSession(sessionId);
        return toResponse(result);
    }

    private IngestResponse toResponse(IngestResult result) {
        return new IngestResponse(
                result.code().name(),
                result.messageId(),
                result.sessionId(),
                result.duplicate()
        );
    }

    public record StartSessionBody(
            @NotBlank String sessionId,
            String title,
            String source,
            List<String> tags,
            Map<String, Object> metadata
    ) {
    }

    public record RecordMessageBody(
            @NotBlank String sessionId,
            @NotBlank String role,
            @NotBlank String content,
            @NotBlank String clientMessageId,
            Map<String, Object> metadata
    ) {
    }

    public record IngestResponse(
            String code,
            String messageId,
            String sessionId,
            boolean duplicate
    ) {
    }
}
