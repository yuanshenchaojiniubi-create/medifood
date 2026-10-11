package com.medifood.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.recipe.entity.RecipeComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 食谱评论 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RecipeCommentMapper extends BaseMapper<RecipeComment> {
}

