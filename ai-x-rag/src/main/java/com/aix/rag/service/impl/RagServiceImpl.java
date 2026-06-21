package com.aix.rag.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import com.aix.rag.dto.MessageDTO;
import com.aix.rag.dto.RagSearchDTO;
import com.aix.rag.service.RagService;
import com.aix.rag.vo.RagMessageVo;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.print.Doc;
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

    @Override
    public List<Document> search(RagSearchDTO ragSearchDTO) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(ragSearchDTO.getQuery())
                .topK(ragSearchDTO.getTopK())
                .similarityThreshold(ragSearchDTO.getSimilarityThreshold())
                .build();
        return this.vectorStore.similaritySearch(searchRequest);
    }

    @Override
    public String delete(List<String> messageIdList) {
        this.vectorStore.delete(messageIdList);
        return "success";
    }
}
