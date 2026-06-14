package com.aix.core.service;

import com.aix.common.exception.AixException;
import com.aix.common.model.ApiCode;
import com.aix.core.dto.CreateSessionRequest;
import com.aix.core.dto.IngestResult;
import com.aix.core.dto.RecordMessageRequest;
import com.aix.storage.entity.ChatMessage;
import com.aix.storage.entity.ChatSession;
import com.aix.storage.mapper.ChatMessageMapper;
import com.aix.storage.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRecordServiceImpl implements ChatRecordService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public IngestResult startSession(CreateSessionRequest request) {
        validateSessionId(request.sessionId());

        ChatSession existing = findSessionBySessionId(request.sessionId());
        if (existing != null) {
            return new IngestResult(ApiCode.OK, null, existing.getSessionId(), true);
        }

        ChatSession session = new ChatSession();
        session.setSessionId(request.sessionId());
        session.setTitle(request.title());
        session.setSource(StringUtils.hasText(request.source()) ? request.source() : "cursor");
        session.setStatus("active");
        session.setMetadataJson(toJson(mergeMetadata(request.metadata(), request.tags())));
        session.setStartedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);

        return new IngestResult(ApiCode.OK, null, session.getSessionId(), false);
    }

    @Override
    @Transactional
    public IngestResult recordMessage(RecordMessageRequest request) {
        validateSessionId(request.sessionId());
        if (!StringUtils.hasText(request.role()) || !StringUtils.hasText(request.content())) {
            throw new AixException(ApiCode.INVALID_ARGUMENT, "role and content are required");
        }
        if (!StringUtils.hasText(request.clientMessageId())) {
            throw new AixException(ApiCode.INVALID_ARGUMENT, "clientMessageId is required");
        }

        ensureSessionExists(request.sessionId());

        ChatMessage duplicate = chatMessageMapper.selectOne(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, request.sessionId())
                        .eq(ChatMessage::getClientMessageId, request.clientMessageId())
        );
        if (duplicate != null) {
            return new IngestResult(ApiCode.DUPLICATE_MESSAGE, duplicate.getMessageId(), request.sessionId(), true);
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId(request.sessionId());
        message.setRole(request.role());
        message.setContent(request.content());
        message.setClientMessageId(request.clientMessageId());
        message.setSeq(nextSeq(request.sessionId()));
        message.setMetadataJson(toJson(request.metadata()));
        chatMessageMapper.insert(message);

        if ("user".equalsIgnoreCase(request.role())) {
            log.debug("enqueue embedding for messageId={}", message.getMessageId());
            // TODO(M2): Spring AI EmbeddingModel + VectorStore async indexing
        }

        return new IngestResult(ApiCode.OK, message.getMessageId(), request.sessionId(), false);
    }

    @Override
    @Transactional
    public IngestResult endSession(String sessionId) {
        validateSessionId(sessionId);

        ChatSession session = findSessionBySessionId(sessionId);
        if (session == null) {
            throw new AixException(ApiCode.SESSION_NOT_FOUND, "session not found: " + sessionId);
        }
        if ("ended".equals(session.getStatus())) {
            return new IngestResult(ApiCode.OK, null, sessionId, true);
        }

        session.setStatus("ended");
        session.setEndedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        log.debug("session ended, trigger summary/extract for sessionId={}", sessionId);
        // TODO(M3): end_session summary and knowledge extraction via Spring AI ChatClient

        return new IngestResult(ApiCode.OK, null, sessionId, false);
    }

    private void ensureSessionExists(String sessionId) {
        if (findSessionBySessionId(sessionId) != null) {
            return;
        }
        startSession(new CreateSessionRequest(sessionId, null, "cursor", null, Map.of("autoCreated", true)));
    }

    private ChatSession findSessionBySessionId(String sessionId) {
        return chatSessionMapper.selectOne(
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getSessionId, sessionId)
        );
    }

    private int nextSeq(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getSeq)
                .last("LIMIT 1");
        ChatMessage latest = chatMessageMapper.selectOne(wrapper);
        return latest == null ? 1 : latest.getSeq() + 1;
    }

    private void validateSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new AixException(ApiCode.INVALID_ARGUMENT, "sessionId is required");
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> metadata, java.util.List<String> tags) {
        Map<String, Object> merged = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        if (tags != null && !tags.isEmpty()) {
            merged.put("tags", tags);
        }
        return merged;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new AixException(ApiCode.INVALID_ARGUMENT, "invalid metadata json");
        }
    }
}
