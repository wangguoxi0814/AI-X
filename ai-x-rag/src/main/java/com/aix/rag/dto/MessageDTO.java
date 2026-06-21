package com.aix.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageDTO {

    private String messageId;

    @NotBlank(message = "文本内容不能为空！")
    private String text;
}
