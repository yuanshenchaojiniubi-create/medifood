package com.medifood.knowledge.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 知识发布 DTO
 * 
 * @author MediFood Team
 */
@Data
public class KnowledgePublishDTO {
    
    @NotBlank(message = "标题不能为空")
    private String title;
    
    @NotBlank(message = "内容不能为空")
    private String content;
    
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    
    private String coverImage;
    
    private List<String> images;
    
    private List<String> ingredients;
}

