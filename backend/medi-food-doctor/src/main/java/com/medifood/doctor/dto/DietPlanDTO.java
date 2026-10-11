package com.medifood.doctor.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 膳食计划 DTO
 *
 * @author MediFood Team
 */
@Data
public class DietPlanDTO {

    private Long id;

    /** 医师ID */
    private Long doctorId;
    /** 医师姓名 */
    private String doctorName;
    /** 医师头像 */
    private String doctorAvatar;
    /** 医师医院 */
    private String doctorHospital;
    /** 医师职称 */
    private String doctorTitle;

    /** 患者ID */
    private Long patientId;
    /** 患者姓名 */
    private String patientName;
    /** 患者头像 */
    private String patientAvatar;

    /** 计划标题 */
    private String title;
    /** 计划描述 */
    private String description;
    /** 详细内容 */
    private String content;
    /** 针对症状 */
    private String targetSymptoms;
    /** 建议执行天数 */
    private Integer durationDays;
    /** 注意事项 */
    private String notes;
    /** 状态 */
    private Integer status;
    /** 浏览次数 */
    private Integer viewCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}