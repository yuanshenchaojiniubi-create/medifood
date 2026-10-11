package com.medifood.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.recipe.entity.RecipeLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 食谱点赞 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RecipeLikeMapper extends BaseMapper<RecipeLike> {
}

