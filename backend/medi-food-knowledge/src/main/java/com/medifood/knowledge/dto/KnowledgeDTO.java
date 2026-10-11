package com.medifood.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识详情 DTO
 * 
 * @author MediFood Team
 */
@Data
public class KnowledgeDTO {
    
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private String title;
    
    private String content;
    
    private Long categoryId;
    
    private String categoryName;
    
    private String coverImage;
    
    private List<String> images;
    
    private List<String> ingredients;
    
    private Integer viewCount;
    
    private Integer likeCount;
    
    private Integer commentCount;
    
    private Integer collectCount;
    
    private Boolean isLiked;
    
    private Boolean isCollected;

    /** 发布者是否为认证医师 */
    private Boolean isDoctor;

    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

