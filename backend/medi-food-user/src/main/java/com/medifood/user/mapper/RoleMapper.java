package com.medifood.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}

