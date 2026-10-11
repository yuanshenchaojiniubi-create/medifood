package com.medifood.doctor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医师工作台仪表盘数据 DTO
 *
 * @author MediFood Team
 */
@Data
public class DoctorWorkspaceDTO {

    /** 今日新增膳食计划数 */
    private Integer todayPlanCount;

    /** 累计膳食计划数 */
    private Integer totalPlanCount;

    /** 待回复咨询数 */
    private Integer pendingConsultationCount;

    /** 累计咨询数 */
    private Integer totalConsultationCount;

    /** 患者总数 */
    private Integer patientCount;

    /** 最近膳食计划 */
    private List<DietPlanSummaryDTO> recentPlans;

    /** 待回复咨询列表 */
    private List<DietConsultationDTO> pendingConsultations;

    @Data
    public static class DietPlanSummaryDTO {
        private Long id;
        private String title;
        private String patientName;
        private LocalDateTime createTime;
    }
}