package com.medifood.user.dto;

import lombok.Data;

/**
 * 医师认证申请 DTO（含专业信息）
 *
 * @author MediFood Team
 */
@Data
public class DoctorCertApplyDTO {

    /**
     * 执业说明
     */
    private String description;

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