package com.aix.rag.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import com.aix.rag.dto.MessageDTO;
import com.aix.rag.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagServiceImpl implements RagService {

    @Autowired
    private VectorStore vectorStore;

    @Override
    public void add(List<MessageDTO> messageDTOList) {
        List<Document> documentList = CollStreamUtil.toList(messageDTOList, messageDTO ->
                Document.builder()
                        .text(messageDTO.getText())
                        .build());
        vectorStore.add(documentList);
    }
}
