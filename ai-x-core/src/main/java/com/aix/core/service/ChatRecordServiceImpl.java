package com.aix.core.service;

import com.aix.common.exception.AixException;
import com.aix.common.model.ApiCode;
import com.aix.core.dto.CreateSessionRequest;
import com.aix.core.dto.IngestResult;
import com.aix.core.dto.RecordMessageRequest;
import com.aix.storage.entity.MessageEntity;
import com.aix.storage.entity.SessionEntity;
import com.aix.storage.mapper.MessageMapper;
import com.aix.storage.mapper.SessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatRecordServiceImpl implements ChatRecordService {

    private static final Logger log = LoggerFactory.getLogger(ChatRecordServiceImpl.class);

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ChatRecordServiceImpl(SessionMapper sessionMapper,
                                 MessageMapper messageMapper,
                                 ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public IngestResult startSession(CreateSessionRequest request) {
        validateSessionId(request.sessionId());

        SessionEntity existing = findSessionBySessionId(request.sessionId());
        if (existing != null) {
            return new IngestResult(ApiCode.OK, null, existing.getSessionId(), true);
        }

        SessionEntity session = new SessionEntity();
        session.setSessionId(request.sessionId());
        session.setTitle(request.title());
        session.setSource(StringUtils.hasText(request.source()) ? request.source() : "cursor");
        session.setStatus("active");
        session.setMetadataJson(toJson(mergeMetadata(request.metadata(), request.tags())));
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);

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

        MessageEntity duplicate = messageMapper.selectOne(
                Wrappers.<MessageEntity>lambdaQuery()
                        .eq(MessageEntity::getSessionId, request.sessionId())
                        .eq(MessageEntity::getClientMessageId, request.clientMessageId())
        );
        if (duplicate != null) {
            return new IngestResult(ApiCode.DUPLICATE_MESSAGE, duplicate.getMessageId(), request.sessionId(), true);
        }

        MessageEntity message = new MessageEntity();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId(request.sessionId());
        message.setRole(request.role());
        message.setContent(request.content());
        message.setClientMessageId(request.clientMessageId());
        message.setSeq(nextSeq(request.sessionId()));
        message.setMetadataJson(toJson(request.metadata()));
        messageMapper.insert(message);

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

        SessionEntity session = findSessionBySessionId(sessionId);
        if (session == null) {
            throw new AixException(ApiCode.SESSION_NOT_FOUND, "session not found: " + sessionId);
        }
        if ("ended".equals(session.getStatus())) {
            return new IngestResult(ApiCode.OK, null, sessionId, true);
        }

        session.setStatus("ended");
        session.setEndedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

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

    private SessionEntity findSessionBySessionId(String sessionId) {
        return sessionMapper.selectOne(
                Wrappers.<SessionEntity>lambdaQuery()
                        .eq(SessionEntity::getSessionId, sessionId)
        );
    }

    private int nextSeq(String sessionId) {
        LambdaQueryWrapper<MessageEntity> wrapper = Wrappers.<MessageEntity>lambdaQuery()
                .eq(MessageEntity::getSessionId, sessionId)
                .orderByDesc(MessageEntity::getSeq)
                .last("LIMIT 1");
        MessageEntity latest = messageMapper.selectOne(wrapper);
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
