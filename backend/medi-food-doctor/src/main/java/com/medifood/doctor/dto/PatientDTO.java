package com.medifood.doctor.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 患者简要信息 DTO
 *
 * @author MediFood Team
 */
@Data
public class PatientDTO {

    private Long userId;
    private String nickname;
    private String avatar;
    /** 最近交互时间 */
    private LocalDateTime lastInteractionTime;
    /** 膳食计划数 */
    private Integer planCount;
    /** 咨询数 */
    private Integer consultationCount;
}