package com.medifoodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifoodai.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话消息 Mapper
 *
 * @author MediFood Team
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}