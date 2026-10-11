package com.medifoodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifoodai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper
 *
 * @author MediFood Team
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}