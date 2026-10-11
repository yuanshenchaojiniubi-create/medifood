package com.medifood.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.knowledge.entity.KnowledgeComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识评论 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface KnowledgeCommentMapper extends BaseMapper<KnowledgeComment> {
}

