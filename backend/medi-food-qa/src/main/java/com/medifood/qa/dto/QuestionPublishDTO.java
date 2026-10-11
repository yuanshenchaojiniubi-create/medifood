package com.medifood.qa.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class QuestionPublishDTO {
    
    @NotBlank(message = "问题标题不能为空")
    private String title;
    
    @NotBlank(message = "问题内容不能为空")
    private String content;
    
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    
    private List<String> images;
}

