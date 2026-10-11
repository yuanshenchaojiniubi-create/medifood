package com.medifood.doctor.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询回复 DTO
 *
 * @author MediFood Team
 */
@Data
public class ConsultationReplyDTO {

    private Long id;
    private Long consultationId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String images;
    private Boolean isDoctorReply;
    private LocalDateTime createTime;
}