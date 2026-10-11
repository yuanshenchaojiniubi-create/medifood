package com.medifood.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食谱标签实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("recipe_tag")
public class RecipeTag {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String tagName;
    
    private String tagType;
    
    private LocalDateTime createTime;
}

