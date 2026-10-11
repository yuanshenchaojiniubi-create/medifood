package com.medifood.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.knowledge.entity.KnowledgeLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识点赞 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface KnowledgeLikeMapper extends BaseMapper<KnowledgeLike> {
}

