package com.medifoodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifoodai.entity.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 反馈 Mapper
 *
 * @author MediFood Team
 */
@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {
}