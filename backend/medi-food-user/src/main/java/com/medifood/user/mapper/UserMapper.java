package com.medifood.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medifood.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 * 
 * @author MediFood Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}

