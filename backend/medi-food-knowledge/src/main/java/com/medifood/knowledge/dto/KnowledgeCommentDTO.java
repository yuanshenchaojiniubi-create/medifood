package com.medifood.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识评论 DTO
 * 
 * @author MediFood Team
 */
@Data
public class KnowledgeCommentDTO {
    
    private Long id;
    
    private Long knowledgeId;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private Long parentId;
    
    private String content;
    
    private Integer likeCount;
    
    private Boolean isLiked;
    
    private List<KnowledgeCommentDTO> replies;
    
    private LocalDateTime createTime;
}

