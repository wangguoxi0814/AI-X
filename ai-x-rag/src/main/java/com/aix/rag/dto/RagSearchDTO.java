package com.aix.rag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Value;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RagSearchDTO {

    /**
     * 消息ID,对应vectorstore的id
     */
    private String messageId;

    /**
     * 查询内容
     */
    @NotBlank(message = "查询内容不能为空!")
    private String query;

    /**
     * topK
     */
    @Min(value = 1, message = "topK 不能小于1")
    private Integer topK;

    /**
     * 相似度阈值
     */
    @Min(value = 0, message = "相似度阈值不能小于0")
    private Double similarityThreshold;
}
