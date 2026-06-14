package com.aix.api.controller.ingest;

import com.aix.common.model.ApiCode;
import com.aix.core.dto.CreateSessionRequest;
import com.aix.core.dto.IngestResult;
import com.aix.core.dto.RecordMessageRequest;
import com.aix.core.service.ChatRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@Slf4j
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
        log.info(
                "ingest message sessionId={} messageId={} messageType={} event={} contentLen={} content={}",
                body.sessionId(),
                body.messageId(),
                body.messageType(),
                body.event(),
                body.content() == null ? 0 : body.content().length(),
                body.content()
        );
        IngestResult result = chatRecordService.recordMessage(
                new RecordMessageRequest(
                        body.sessionId(),
                        body.messageType(),
                        body.content(),
                        body.messageId(),
                        body.event(),
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
            @NotBlank(message = "sessionId 会话 ID 不能为空")
            String sessionId,
            String title,
            String source,
            List<String> tags,
            Map<String, Object> metadata
    ) {
    }

    public record RecordMessageBody(
            @NotBlank(message = "sessionId 会话 ID 不能为空")
            String sessionId,
            @NotNull(message = "messageType 消息类型不能为空，1-user 2-assistant 3-thought 4-system")
            Integer messageType,
            @NotBlank(message = "content 消息正文不能为空")
            String content,
            @NotBlank(message = "messageId 消息 ID 不能为空")
            String messageId,
            @NotNull(message = "event 钩子事件码不能为空")
            Integer event,
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
