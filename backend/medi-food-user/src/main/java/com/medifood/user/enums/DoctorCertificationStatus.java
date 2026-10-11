package com.medifood.user.enums;

/**
 * 医师资格认证状态
 *
 * @author MediFood
 */
public enum DoctorCertificationStatus {

    /**
     * 未提交
     */
    UNSUBMITTED,

    /**
     * 审核中
     */
    PENDING,

    /**
     * 已通过
     */
    APPROVED,

    /**
     * 被拒绝
     */
    REJECTED
}

