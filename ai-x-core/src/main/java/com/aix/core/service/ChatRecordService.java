package com.aix.core.service;

import com.aix.core.dto.CreateSessionRequest;
import com.aix.core.dto.IngestResult;
import com.aix.core.dto.RecordMessageRequest;

public interface ChatRecordService {

    IngestResult startSession(CreateSessionRequest request);

    IngestResult recordMessage(RecordMessageRequest request);

    IngestResult endSession(String sessionId);
}
