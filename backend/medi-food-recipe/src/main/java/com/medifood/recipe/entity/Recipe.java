package com.medifood.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康食谱实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("recipe")
public class Recipe {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String title;
    
    private String description;
    
    private String ingredients;
    
    private String steps;
    
    private String suitableFor;
    
    /**
     * 适合的节气/季节（逗号分隔，如 "立春,春分,春季"）
     * 用于节气食谱精准推荐，优先级高于关键词搜索
     */
    private String suitableSeasons;
    
    private String effects;
    
    private String coverImage;
    
    private String images;
    
    private Integer difficulty;
    
    private Integer cookingTime;
    
    private Integer servings;
    
    private Integer viewCount;
    
    private Integer likeCount;
    
    private Integer commentCount;
    
    private Integer collectCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

