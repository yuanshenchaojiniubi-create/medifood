package com.medifood.doctor.dto;

import lombok.Data;

/**
 * 创建/更新膳食计划 DTO
 *
 * @author MediFood Team
 */
@Data
public class DietPlanCreateDTO {

    /** 患者ID */
    private Long patientId;

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
}