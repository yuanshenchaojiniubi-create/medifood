package com.medifood.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * 食谱查询 DTO
 * 
 * @author MediFood Team
 */
@Data
public class RecipeQueryDTO {
    
    private Integer page = 1;
    
    private Integer size = 10;
    
    private List<Long> tagIds;
    
    private String tagType;
    
    private String keyword;
    
    private String sort = "latest";
}

