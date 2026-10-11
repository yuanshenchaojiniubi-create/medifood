package com.medifood.user.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录 DTO
 * 
 * @author MediFood Team
 */
@Data
public class UserLoginDTO {
    
    /**
     * 用户名/邮箱/手机号
     */
    @NotBlank(message = "账号不能为空")
    private String account;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}

