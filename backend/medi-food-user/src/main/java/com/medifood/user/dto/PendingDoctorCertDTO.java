package com.medifood.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核医师认证列表项
 */
@Data
public class PendingDoctorCertDTO {

    private Long userId;
    private String username;
    private String nickname;
    private String doctorCertFile;
    private String doctorCertDescription;
    private LocalDateTime doctorCertAppliedAt;
}
