package com.medifood.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.knowledge.entity.KnowledgeIngredient;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识食材关联 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface KnowledgeIngredientMapper extends BaseMapper<KnowledgeIngredient> {
}

