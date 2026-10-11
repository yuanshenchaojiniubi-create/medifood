package com.medifood.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食谱评论实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("recipe_comment")
public class RecipeComment {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long recipeId;
    
    private Long userId;
    
    private Long parentId;
    
    private String content;
    
    private Integer likeCount;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

