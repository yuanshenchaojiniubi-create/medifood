package com.medifood.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * @author MediFood Team
 */
@Data
@TableName("user")
public class User {
    
    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码（加密后）
     */
    private String password;
    
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
     * 是否为认证医师：0-否，1-是
     */
    private Integer isDoctor;
    
    /**
     * 医师认证状态
     */
    private String doctorCertStatus;
    
    /**
     * 医师认证材料路径
     */
    private String doctorCertFile;
    
    /**
     * 医师认证说明
     */
    private String doctorCertDescription;
    
    /**
     * 医师认证审核说明
     */
    private String doctorCertReason;
    
    /**
     * 医师认证申请时间
     */
    private LocalDateTime doctorCertAppliedAt;
    
    /**
     * 医师认证审核时间
     */
    private LocalDateTime doctorCertReviewedAt;
    
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
     * 状态：0-禁用，1-启用
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

