package com.aix.api.controller.rag;

import cn.hutool.core.collection.CollStreamUtil;
import com.aix.rag.dto.MessageDTO;
import com.aix.rag.dto.RagSearchDTO;
import com.aix.rag.service.RagService;
import com.aix.rag.vo.RagMessageVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rag")
@Validated
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/embedding/add")
    public String add(@Valid @RequestBody List<MessageDTO> messageDTOList) {
        ragService.add(messageDTOList);
        return "success";
    }

    @PostMapping("/embedding/search")
    public List<RagMessageVo> search(@Valid @RequestBody RagSearchDTO ragSearchDTO) {
        List<Document> documentList = ragService.search(ragSearchDTO);
        return CollStreamUtil.toList(documentList, document ->
                new RagMessageVo()
                        .setText(document.getText())
                        .setMessageId(document.getId())
        );
    }

    @DeleteMapping("/embedding/delete")
    public String delete(@RequestBody @NotEmpty List<String> messageIdList) {
        return ragService.delete(messageIdList);
    }
}
