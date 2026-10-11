package com.medifood.recipe.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 食谱详情 DTO
 * 
 * @author MediFood Team
 */
@Data
public class RecipeDTO {
    
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private String title;
    
    private String description;
    
    private List<Map<String, Object>> ingredients;
    
    private List<Map<String, Object>> steps;
    
    private String suitableFor;
    
    private String effects;
    
    private String coverImage;
    
    private List<String> images;
    
    private Integer difficulty;
    
    private Integer cookingTime;
    
    private Integer servings;
    
    private List<String> tags;
    
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

