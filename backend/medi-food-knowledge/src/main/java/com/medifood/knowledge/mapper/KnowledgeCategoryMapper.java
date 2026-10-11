package com.medifood.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.knowledge.entity.KnowledgeCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识分类 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}

