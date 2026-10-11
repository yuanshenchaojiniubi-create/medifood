package com.medifood.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.user.entity.User;
import com.medifood.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
    @Select("SELECT role_id FROM user_role WHERE user_id = #{userId}")
    Long selectRoleIdByUserId(Long userId);
}