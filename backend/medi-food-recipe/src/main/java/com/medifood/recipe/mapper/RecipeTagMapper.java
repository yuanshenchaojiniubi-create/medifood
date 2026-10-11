package com.medifood.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.recipe.entity.RecipeTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 食谱标签 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RecipeTagMapper extends BaseMapper<RecipeTag> {
}

