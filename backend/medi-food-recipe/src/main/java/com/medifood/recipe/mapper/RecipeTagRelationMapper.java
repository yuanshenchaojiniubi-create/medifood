package com.medifood.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.recipe.entity.RecipeTagRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 食谱标签关联 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RecipeTagRelationMapper extends BaseMapper<RecipeTagRelation> {
}

