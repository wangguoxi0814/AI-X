package com.aix.rag.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RagMessageVo {

    private String messageId;

    private String text;
}
