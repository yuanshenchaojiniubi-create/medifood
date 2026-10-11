package com.medifood.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员审核医师认证
 */
@Data
public class DoctorCertReviewDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 是否通过
     */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /**
     * 驳回原因（驳回时建议填写）
     */
    private String reason;
}
