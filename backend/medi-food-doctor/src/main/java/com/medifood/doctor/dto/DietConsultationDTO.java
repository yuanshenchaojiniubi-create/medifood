package com.medifood.doctor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 膳食咨询 DTO
 *
 * @author MediFood Team
 */
@Data
public class DietConsultationDTO {

    private Long id;

    /** 患者ID */
    private Long patientId;
    /** 患者姓名 */
    private String patientName;
    /** 患者头像 */
    private String patientAvatar;

    /** 医师ID */
    private Long doctorId;
    /** 医师姓名 */
    private String doctorName;
    /** 医师头像 */
    private String doctorAvatar;

    /** 咨询标题 */
    private String title;
    /** 咨询内容 */
    private String content;
    /** 图片 */
    private String images;
    /** 状态 */
    private String status;
    /** 回复数 */
    private Integer replyCount;

    /** 回复列表 */
    private List<ConsultationReplyDTO> replies;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}