package com.medifood.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.doctor.entity.DietPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 膳食计划 Mapper
 *
 * @author MediFood Team
 */
@Mapper
public interface DietPlanMapper extends BaseMapper<DietPlan> {
}