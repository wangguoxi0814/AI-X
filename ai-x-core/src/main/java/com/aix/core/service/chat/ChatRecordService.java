package com.aix.core.service.chat;

import com.aix.core.dto.chat.CreateSessionRequest;
import com.aix.core.dto.chat.IngestResult;
import com.aix.core.dto.chat.RecordMessageRequest;

public interface ChatRecordService {

    IngestResult startSession(CreateSessionRequest request);

    IngestResult recordMessage(RecordMessageRequest request);

    IngestResult endSession(String sessionId);
}
