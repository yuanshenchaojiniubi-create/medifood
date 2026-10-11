package com.medifood.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食谱点赞实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("recipe_like")
public class RecipeLike {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long recipeId;
    
    private Long userId;
    
    private LocalDateTime createTime;
}

