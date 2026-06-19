package com.aix.rag.service;

import com.aix.rag.dto.MessageDTO;
import com.aix.rag.entity.RagMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface RagService {

    void add(List<MessageDTO> messageDTOList);
}
