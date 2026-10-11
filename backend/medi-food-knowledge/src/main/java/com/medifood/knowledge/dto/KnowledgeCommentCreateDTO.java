package com.medifood.knowledge.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评论 DTO
 * 
 * @author MediFood Team
 */
@Data
public class KnowledgeCommentCreateDTO {
    
    @NotNull(message = "知识ID不能为空")
    private Long knowledgeId;
    
    private Long parentId;
    
    @NotBlank(message = "评论内容不能为空")
    private String content;
}

