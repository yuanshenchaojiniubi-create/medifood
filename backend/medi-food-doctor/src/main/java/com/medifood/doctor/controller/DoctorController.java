package com.medifood.doctor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.doctor.dto.*;
import com.medifood.doctor.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 医师服务控制器
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * 获取医师工作台仪表盘数据
     */
    @GetMapping("/workspace")
    public Result<DoctorWorkspaceDTO> workspace(@CurrentUser Long doctorId) {
        return Result.success(doctorService.getWorkspaceData(doctorId));
    }

    // ============ 膳食计划 ============

    /**
     * 创建膳食计划
     */
    @PostMapping("/diet-plan")
    public Result<Long> createDietPlan(@CurrentUser Long doctorId,
                                       @RequestBody DietPlanCreateDTO dto) {
        return Result.success("创建成功", doctorService.createDietPlan(doctorId, dto));
    }

    /**
     * 更新膳食计划
     */
    @PutMapping("/diet-plan/{id}")
    public Result<?> updateDietPlan(@CurrentUser Long doctorId,
                                    @PathVariable Long id,
                                    @RequestBody DietPlanCreateDTO dto) {
        doctorService.updateDietPlan(doctorId, id, dto);
        return Result.success("更新成功");
    }

    /**
     * 删除膳食计划
     */
    @DeleteMapping("/diet-plan/{id}")
    public Result<?> deleteDietPlan(@CurrentUser Long doctorId,
                                    @PathVariable Long id) {
        doctorService.deleteDietPlan(doctorId, id);
        return Result.success("删除成功");
    }

    /**
     * 获取医师的膳食计划列表
     */
    @GetMapping("/diet-plans")
    public Result<Page<DietPlanDTO>> getMyDietPlans(@CurrentUser Long doctorId,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) Long patientId) {
        return Result.success(doctorService.getMyDietPlans(doctorId, page, size, patientId));
    }

    /**
     * 获取指定患者的膳食计划列表
     */
    @GetMapping("/patient/{patientId}/diet-plans")
    public Result<List<DietPlanDTO>> getPatientDietPlans(@CurrentUser(required = false) Long currentUserId,
                                                          @PathVariable Long patientId) {
        return Result.success(doctorService.getPatientDietPlans(patientId, currentUserId));
    }

    /**
     * 获取膳食计划详情
     */
    @GetMapping("/diet-plan/{id}")
    public Result<DietPlanDTO> getDietPlanDetail(@CurrentUser(required = false) Long currentUserId,
                                                  @PathVariable Long id) {
        return Result.success(doctorService.getDietPlanDetail(id, currentUserId));
    }

    // ============ 咨询管理 ============

    /**
     * 发起咨询
     */
    @PostMapping("/consultation")
    public Result<Long> createConsultation(@CurrentUser Long patientId,
                                           @RequestBody DietConsultationCreateDTO dto) {
        return Result.success("咨询已提交", doctorService.createConsultation(patientId, dto));
    }

    /**
     * 获取医师的咨询列表
     */
    @GetMapping("/consultations")
    public Result<Page<DietConsultationDTO>> getDoctorConsultations(@CurrentUser Long doctorId,
                                                                     @RequestParam(defaultValue = "1") Integer page,
                                                                     @RequestParam(defaultValue = "10") Integer size,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(doctorService.getDoctorConsultations(doctorId, page, size, status));
    }

    /**
     * 获取我的咨询列表（患者视角）
     */
    @GetMapping("/my-consultations")
    public Result<Page<DietConsultationDTO>> getMyConsultations(@CurrentUser Long patientId,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(doctorService.getMyConsultations(patientId, page, size));
    }

    /**
     * 获取咨询详情
     */
    @GetMapping("/consultation/{id}")
    public Result<DietConsultationDTO> getConsultationDetail(@CurrentUser(required = false) Long currentUserId,
                                                              @PathVariable Long id) {
        return Result.success(doctorService.getConsultationDetail(id, currentUserId));
    }

    /**
     * 回复咨询
     */
    @PostMapping("/consultation/{id}/reply")
    public Result<Long> replyConsultation(@CurrentUser Long userId,
                                          @PathVariable Long id,
                                          @RequestBody ConsultationReplyDTO dto) {
        return Result.success("回复成功", doctorService.replyConsultation(userId, id, dto));
    }

    /**
     * 关闭咨询
     */
    @PutMapping("/consultation/{id}/close")
    public Result<?> closeConsultation(@CurrentUser Long userId,
                                       @PathVariable Long id) {
        doctorService.closeConsultation(userId, id);
        return Result.success("咨询已关闭");
    }

    // ============ 患者管理 ============

    /**
     * 获取患者列表
     */
    @GetMapping("/patients")
    public Result<Page<PatientDTO>> getPatients(@CurrentUser Long doctorId,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(doctorService.getPatients(doctorId, page, size));
    }
}