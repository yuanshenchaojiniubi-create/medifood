package com.medifood.recipe.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 食谱发布 DTO
 * 
 * @author MediFood Team
 */
@Data
public class RecipePublishDTO {
    
    @NotBlank(message = "食谱名称不能为空")
    private String title;
    
    private String description;
    
    @NotNull(message = "食材组成不能为空")
    private List<Map<String, Object>> ingredients;
    
    @NotNull(message = "制作步骤不能为空")
    private List<Map<String, Object>> steps;
    
    private String suitableFor;
    
    private String effects;
    
    private String coverImage;
    
    private List<String> images;
    
    private Integer difficulty;
    
    private Integer cookingTime;
    
    private Integer servings;
    
    private List<Long> tagIds;
}

