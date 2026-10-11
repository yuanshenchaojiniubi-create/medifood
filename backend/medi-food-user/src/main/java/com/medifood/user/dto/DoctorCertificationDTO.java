package com.medifood.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 医师资格认证信息
 *
 * @author MediFood
 */
@Data
public class DoctorCertificationDTO {

    /**
     * 认证状态
     */
    private String status;

    /**
     * 认证材料地址
     */
    private String certificateUrl;

    /**
     * 认证说明
     */
    private String description;

    /**
     * 审核说明
     */
    private String reason;

    /**
     * 申请时间
     */
    private LocalDateTime appliedAt;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 是否已是医师
     */
    private Boolean doctor;
    
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
}

