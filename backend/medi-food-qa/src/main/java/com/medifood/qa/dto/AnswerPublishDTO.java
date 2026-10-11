package com.medifood.qa.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class AnswerPublishDTO {
    
    @NotNull(message = "问题ID不能为空")
    private Long questionId;
    
    @NotBlank(message = "回答内容不能为空")
    private String content;
    
    private List<String> images;
}

