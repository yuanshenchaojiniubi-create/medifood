package com.medifood.user.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户信息 DTO
 * 
 * @author MediFood Team
 */
@Data
public class UserInfoDTO {
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatar;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 性别：0-未知，1-男，2-女
     */
    private Integer gender;
    
    /**
     * 生日
     */
    private LocalDate birthday;
    
    /**
     * 个人简介
     */
    private String bio;
    
    /**
     * 是否为认证医师
     */
    private Boolean isDoctor;
    
    /**
     * 医师认证状态
     */
    private String doctorCertStatus;
    
    /**
     * 医师认证说明
     */
    private String doctorCertDescription;
    
    /**
     * 医师认证审核说明
     */
    private String doctorCertReason;
    
    /**
     * 执业医院
     */
    private String hospital;
    
    /**
     * 科室
     */
    private String department;
    
    /**
     * 职称
     */
    private String title;
    
    /**
     * 擅长领域（逗号分隔）
     */
    private String specialties;
    
    /**
     * 从业年限
     */
    private Integer practiceYears;
    
    /**
     * 角色列表（role_code，如 USER、ADMIN）
     */
    private String roles;

    /**
     * 是否为系统管理员
     */
    private Boolean isAdmin;

    /*
    * 关注数量
    * */
    private Integer followingCount;
    /*
    * 粉丝数量
    * */
    private Integer followersCount;

    /**
     * 互关数量
     */
    private Integer mutualCount;

    /**
     * 当前用户是否已关注该用户
     */
    private Boolean isFollowing;

}

