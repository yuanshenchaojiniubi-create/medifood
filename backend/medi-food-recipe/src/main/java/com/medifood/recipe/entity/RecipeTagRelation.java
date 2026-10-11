package com.medifood.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食谱标签关联实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("recipe_tag_relation")
public class RecipeTagRelation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long recipeId;
    
    private Long tagId;
    
    private LocalDateTime createTime;
}

