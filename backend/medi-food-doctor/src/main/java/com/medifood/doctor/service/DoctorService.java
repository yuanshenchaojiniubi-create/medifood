package com.medifood.doctor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.doctor.dto.*;

import java.util.List;

/**
 * 医师服务接口
 *
 * @author MediFood Team
 */
public interface DoctorService {

    /**
     * 获取工作台仪表盘数据
     */
    DoctorWorkspaceDTO getWorkspaceData(Long doctorId);

    // ============ 膳食计划 ============

    /**
     * 创建膳食计划
     */
    Long createDietPlan(Long doctorId, DietPlanCreateDTO dto);

    /**
     * 更新膳食计划
     */
    void updateDietPlan(Long doctorId, Long planId, DietPlanCreateDTO dto);

    /**
     * 删除膳食计划
     */
    void deleteDietPlan(Long doctorId, Long planId);

    /**
     * 获取医师的膳食计划列表
     */
    Page<DietPlanDTO> getMyDietPlans(Long doctorId, Integer page, Integer size, Long patientId);

    /**
     * 获取指定患者的膳食计划列表
     */
    List<DietPlanDTO> getPatientDietPlans(Long patientId, Long currentUserId);

    /**
     * 获取膳食计划详情
     */
    DietPlanDTO getDietPlanDetail(Long planId, Long currentUserId);

    // ============ 咨询管理 ============

    /**
     * 发起咨询
     */
    Long createConsultation(Long patientId, DietConsultationCreateDTO dto);

    /**
     * 获取医师的咨询列表
     */
    Page<DietConsultationDTO> getDoctorConsultations(Long doctorId, Integer page, Integer size, String status);

    /**
     * 获取患者的咨询列表
     */
    Page<DietConsultationDTO> getMyConsultations(Long patientId, Integer page, Integer size);

    /**
     * 获取咨询详情
     */
    DietConsultationDTO getConsultationDetail(Long consultationId, Long currentUserId);

    /**
     * 回复咨询
     */
    Long replyConsultation(Long userId, Long consultationId, ConsultationReplyDTO dto);

    /**
     * 关闭咨询
     */
    void closeConsultation(Long userId, Long consultationId);

    // ============ 患者管理 ============

    /**
     * 获取患者列表
     */
    Page<PatientDTO> getPatients(Long doctorId, Integer page, Integer size);
}