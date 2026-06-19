package com.aix.api.controller.rag;

import com.aix.rag.dto.MessageDTO;
import com.aix.rag.service.RagService;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/rag")
@Validated
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/embedding/add")
    public String add(@RequestBody @NotNull List<MessageDTO> messageDTOList) {
        ragService.add(messageDTOList);
        return "success";
    }
}
