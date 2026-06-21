package com.aix.rag.service;

import com.aix.rag.dto.MessageDTO;
import com.aix.rag.dto.RagSearchDTO;
import org.springframework.ai.document.Document;

import javax.validation.constraints.NotNull;
import java.util.List;

public interface RagService {

    void add(List<MessageDTO> messageDTOList);

    List<Document> search(RagSearchDTO ragSearchDTO);

    String delete(List<String> messageIdList);
}
