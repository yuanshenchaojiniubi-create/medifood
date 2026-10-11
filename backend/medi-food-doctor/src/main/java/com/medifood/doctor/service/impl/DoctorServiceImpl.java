package com.medifood.doctor.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifood.doctor.dto.*;
import com.medifood.doctor.entity.ConsultationReply;
import com.medifood.doctor.entity.DietConsultation;
import com.medifood.doctor.entity.DietPlan;
import com.medifood.doctor.feign.UserClient;
import com.medifood.doctor.mapper.ConsultationReplyMapper;
import com.medifood.doctor.mapper.DietConsultationMapper;
import com.medifood.doctor.mapper.DietPlanMapper;
import com.medifood.doctor.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 医师服务实现
 *
 * @author MediFood Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DietPlanMapper dietPlanMapper;
    private final DietConsultationMapper dietConsultationMapper;
    private final ConsultationReplyMapper consultationReplyMapper;
    private final UserClient userClient;

    @Override
    public DoctorWorkspaceDTO getWorkspaceData(Long doctorId) {
        DoctorWorkspaceDTO dto = new DoctorWorkspaceDTO();

        // 统计膳食计划
        LambdaQueryWrapper<DietPlan> planQuery = new LambdaQueryWrapper<DietPlan>()
                .eq(DietPlan::getDoctorId, doctorId);
        dto.setTotalPlanCount(dietPlanMapper.selectCount(planQuery).intValue());

        // 今日新增计划
        dto.setTodayPlanCount(dietPlanMapper.selectCount(
                planQuery.clone().ge(DietPlan::getCreateTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))).intValue());

        // 统计咨询
        LambdaQueryWrapper<DietConsultation> consultQuery = new LambdaQueryWrapper<DietConsultation>()
                .eq(DietConsultation::getDoctorId, doctorId);
        dto.setTotalConsultationCount(dietConsultationMapper.selectCount(consultQuery).intValue());
        dto.setPendingConsultationCount(dietConsultationMapper.selectCount(
                consultQuery.clone().eq(DietConsultation::getStatus, "PENDING")).intValue());

        // 患者数（有膳食计划的去重患者）
        List<DietPlan> allPlans = dietPlanMapper.selectList(planQuery);
        Set<Long> patientIds = new HashSet<>();
        for (DietPlan plan : allPlans) {
            patientIds.add(plan.getPatientId());
        }
        // 再加上咨询的患者
        List<DietConsultation> allConsults = dietConsultationMapper.selectList(consultQuery);
        for (DietConsultation c : allConsults) {
            patientIds.add(c.getPatientId());
        }
        dto.setPatientCount(patientIds.size());

        // 最近5条计划
        List<DietPlan> recentPlans = dietPlanMapper.selectList(
                planQuery.orderByDesc(DietPlan::getCreateTime).last("LIMIT 5"));
        dto.setRecentPlans(recentPlans.stream().map(p -> {
            DoctorWorkspaceDTO.DietPlanSummaryDTO s = new DoctorWorkspaceDTO.DietPlanSummaryDTO();
            s.setId(p.getId());
            s.setTitle(p.getTitle());
            s.setPatientName(getUserName(p.getPatientId()));
            s.setCreateTime(p.getCreateTime());
            return s;
        }).collect(Collectors.toList()));

        // 最近5条待回复咨询
        List<DietConsultation> pendingConsults = dietConsultationMapper.selectList(
                consultQuery.clone().eq(DietConsultation::getStatus, "PENDING")
                        .orderByDesc(DietConsultation::getCreateTime).last("LIMIT 5"));
        dto.setPendingConsultations(pendingConsults.stream().map(c -> convertToConsultationDTO(c, null)).collect(Collectors.toList()));

        return dto;
    }

    // ============ 膳食计划 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDietPlan(Long doctorId, DietPlanCreateDTO dto) {
        assertDoctor(doctorId);
        DietPlan plan = new DietPlan();
        plan.setDoctorId(doctorId);
        plan.setPatientId(dto.getPatientId());
        plan.setTitle(dto.getTitle());
        plan.setDescription(dto.getDescription());
        plan.setContent(dto.getContent());
        plan.setTargetSymptoms(dto.getTargetSymptoms());
        plan.setDurationDays(dto.getDurationDays());
        plan.setNotes(dto.getNotes());
        plan.setStatus(1); // 已发布
        plan.setViewCount(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        dietPlanMapper.insert(plan);
        log.info("医师 {} 创建膳食计划: {}", doctorId, plan.getId());
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDietPlan(Long doctorId, Long planId, DietPlanCreateDTO dto) {
        assertDoctor(doctorId);
        DietPlan plan = dietPlanMapper.selectById(planId);
        if (plan == null || !plan.getDoctorId().equals(doctorId)) {
            throw new BusinessException("无权操作该膳食计划");
        }
        plan.setTitle(dto.getTitle());
        plan.setDescription(dto.getDescription());
        plan.setContent(dto.getContent());
        plan.setTargetSymptoms(dto.getTargetSymptoms());
        plan.setDurationDays(dto.getDurationDays());
        plan.setNotes(dto.getNotes());
        plan.setUpdateTime(LocalDateTime.now());
        dietPlanMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDietPlan(Long doctorId, Long planId) {
        assertDoctor(doctorId);
        DietPlan plan = dietPlanMapper.selectById(planId);
        if (plan == null || !plan.getDoctorId().equals(doctorId)) {
            throw new BusinessException("无权操作该膳食计划");
        }
        dietPlanMapper.deleteById(planId);
    }

    @Override
    public Page<DietPlanDTO> getMyDietPlans(Long doctorId, Integer page, Integer size, Long patientId) {
        LambdaQueryWrapper<DietPlan> query = new LambdaQueryWrapper<DietPlan>()
                .eq(DietPlan::getDoctorId, doctorId);
        if (patientId != null) {
            query.eq(DietPlan::getPatientId, patientId);
        }
        query.orderByDesc(DietPlan::getCreateTime);

        Page<DietPlan> planPage = new Page<>(page, size);
        dietPlanMapper.selectPage(planPage, query);

        Page<DietPlanDTO> dtoPage = new Page<>(page, size, planPage.getTotal());
        List<DietPlanDTO> dtoList = planPage.getRecords().stream()
                .map(p -> convertToPlanDTO(p, null))
                .collect(Collectors.toList());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public List<DietPlanDTO> getPatientDietPlans(Long patientId, Long currentUserId) {
        LambdaQueryWrapper<DietPlan> query = new LambdaQueryWrapper<DietPlan>()
                .eq(DietPlan::getPatientId, patientId)
                .eq(DietPlan::getStatus, 1)
                .orderByDesc(DietPlan::getCreateTime);
        List<DietPlan> plans = dietPlanMapper.selectList(query);
        return plans.stream().map(p -> convertToPlanDTO(p, currentUserId)).collect(Collectors.toList());
    }

    @Override
    public DietPlanDTO getDietPlanDetail(Long planId, Long currentUserId) {
        DietPlan plan = dietPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("膳食计划不存在");
        }
        // 增加浏览次数
        plan.setViewCount(plan.getViewCount() + 1);
        dietPlanMapper.updateById(plan);
        return convertToPlanDTO(plan, currentUserId);
    }

    // ============ 咨询管理 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConsultation(Long patientId, DietConsultationCreateDTO dto) {
        DietConsultation consultation = new DietConsultation();
        consultation.setPatientId(patientId);
        consultation.setDoctorId(dto.getDoctorId());
        consultation.setTitle(dto.getTitle());
        consultation.setContent(dto.getContent());
        consultation.setImages(dto.getImages());
        consultation.setStatus("PENDING");
        consultation.setReplyCount(0);
        consultation.setCreateTime(LocalDateTime.now());
        consultation.setUpdateTime(LocalDateTime.now());
        dietConsultationMapper.insert(consultation);
        log.info("患者 {} 向医师 {} 发起咨询: {}", patientId, dto.getDoctorId(), consultation.getId());
        return consultation.getId();
    }

    @Override
    public Page<DietConsultationDTO> getDoctorConsultations(Long doctorId, Integer page, Integer size, String status) {
        LambdaQueryWrapper<DietConsultation> query = new LambdaQueryWrapper<DietConsultation>()
                .eq(DietConsultation::getDoctorId, doctorId);
        if (StrUtil.isNotBlank(status)) {
            query.eq(DietConsultation::getStatus, status);
        }
        query.orderByDesc(DietConsultation::getCreateTime);

        Page<DietConsultation> consultPage = new Page<>(page, size);
        dietConsultationMapper.selectPage(consultPage, query);

        Page<DietConsultationDTO> dtoPage = new Page<>(page, size, consultPage.getTotal());
        dtoPage.setRecords(consultPage.getRecords().stream()
                .map(c -> convertToConsultationDTO(c, null))
                .collect(Collectors.toList()));
        return dtoPage;
    }

    @Override
    public Page<DietConsultationDTO> getMyConsultations(Long patientId, Integer page, Integer size) {
        LambdaQueryWrapper<DietConsultation> query = new LambdaQueryWrapper<DietConsultation>()
                .eq(DietConsultation::getPatientId, patientId)
                .orderByDesc(DietConsultation::getCreateTime);

        Page<DietConsultation> consultPage = new Page<>(page, size);
        dietConsultationMapper.selectPage(consultPage, query);

        Page<DietConsultationDTO> dtoPage = new Page<>(page, size, consultPage.getTotal());
        dtoPage.setRecords(consultPage.getRecords().stream()
                .map(c -> convertToConsultationDTO(c, null))
                .collect(Collectors.toList()));
        return dtoPage;
    }

    @Override
    public DietConsultationDTO getConsultationDetail(Long consultationId, Long currentUserId) {
        DietConsultation consultation = dietConsultationMapper.selectById(consultationId);
        if (consultation == null) {
            throw new BusinessException("咨询不存在");
        }
        return convertToConsultationDTO(consultation, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long replyConsultation(Long userId, Long consultationId, ConsultationReplyDTO dto) {
        DietConsultation consultation = dietConsultationMapper.selectById(consultationId);
        if (consultation == null) {
            throw new BusinessException("咨询不存在");
        }
        if ("CLOSED".equals(consultation.getStatus())) {
            throw new BusinessException("该咨询已关闭");
        }

        boolean isDoctor = consultation.getDoctorId().equals(userId);
        ConsultationReply reply = new ConsultationReply();
        reply.setConsultationId(consultationId);
        reply.setUserId(userId);
        reply.setContent(dto.getContent());
        reply.setImages(dto.getImages());
        reply.setIsDoctorReply(isDoctor ? 1 : 0);
        reply.setCreateTime(LocalDateTime.now());
        consultationReplyMapper.insert(reply);

        // 更新咨询状态和回复数
        consultation.setStatus("REPLIED");
        consultation.setReplyCount(consultation.getReplyCount() + 1);
        consultation.setUpdateTime(LocalDateTime.now());
        dietConsultationMapper.updateById(consultation);

        return reply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeConsultation(Long userId, Long consultationId) {
        DietConsultation consultation = dietConsultationMapper.selectById(consultationId);
        if (consultation == null) {
            throw new BusinessException("咨询不存在");
        }
        if (!consultation.getDoctorId().equals(userId) && !consultation.getPatientId().equals(userId)) {
            throw new BusinessException("无权操作该咨询");
        }
        consultation.setStatus("CLOSED");
        consultation.setUpdateTime(LocalDateTime.now());
        dietConsultationMapper.updateById(consultation);
    }

    // ============ 患者管理 ============

    @Override
    public Page<PatientDTO> getPatients(Long doctorId, Integer page, Integer size) {
        // 查询所有与该医生有交互的患者
        Set<Long> patientIds = new HashSet<>();

        List<DietPlan> plans = dietPlanMapper.selectList(
                new LambdaQueryWrapper<DietPlan>().eq(DietPlan::getDoctorId, doctorId));
        for (DietPlan p : plans) {
            patientIds.add(p.getPatientId());
        }

        List<DietConsultation> consultations = dietConsultationMapper.selectList(
                new LambdaQueryWrapper<DietConsultation>().eq(DietConsultation::getDoctorId, doctorId));
        for (DietConsultation c : consultations) {
            patientIds.add(c.getPatientId());
        }

        List<PatientDTO> patientList = new ArrayList<>();
        for (Long pid : patientIds) {
            PatientDTO dto = new PatientDTO();
            dto.setUserId(pid);
            dto.setNickname(getUserName(pid));
            dto.setAvatar(getUserAvatar(pid));

            // 统计该患者的计划数和咨询数
            int planCnt = dietPlanMapper.selectCount(
                    new LambdaQueryWrapper<DietPlan>().eq(DietPlan::getDoctorId, doctorId).eq(DietPlan::getPatientId, pid)).intValue();
            int consultCnt = dietConsultationMapper.selectCount(
                    new LambdaQueryWrapper<DietConsultation>().eq(DietConsultation::getDoctorId, doctorId).eq(DietConsultation::getPatientId, pid)).intValue();
            dto.setPlanCount(planCnt);
            dto.setConsultationCount(consultCnt);

            // 最近交互时间
            LocalDateTime lastPlan = null;
            DietPlan lastP = dietPlanMapper.selectOne(
                    new LambdaQueryWrapper<DietPlan>().eq(DietPlan::getDoctorId, doctorId).eq(DietPlan::getPatientId, pid)
                            .orderByDesc(DietPlan::getCreateTime).last("LIMIT 1"));
            if (lastP != null) lastPlan = lastP.getCreateTime();

            DietConsultation lastC = dietConsultationMapper.selectOne(
                    new LambdaQueryWrapper<DietConsultation>().eq(DietConsultation::getDoctorId, doctorId).eq(DietConsultation::getPatientId, pid)
                            .orderByDesc(DietConsultation::getCreateTime).last("LIMIT 1"));
            LocalDateTime lastConsult = lastC != null ? lastC.getCreateTime() : null;

            if (lastPlan != null && lastConsult != null) {
                dto.setLastInteractionTime(lastPlan.isAfter(lastConsult) ? lastPlan : lastConsult);
            } else if (lastPlan != null) {
                dto.setLastInteractionTime(lastPlan);
            } else {
                dto.setLastInteractionTime(lastConsult);
            }

            patientList.add(dto);
        }

        // 分页
        int total = patientList.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<PatientDTO> paginatedList = start < total ? patientList.subList(start, end) : new ArrayList<>();

        Page<PatientDTO> result = new Page<>(page, size, total);
        result.setRecords(paginatedList);
        return result;
    }

    // ============ 私有辅助方法 ============

    /**
     * 验证医师身份
     */
    private void assertDoctor(Long userId) {
        try {
            Result<Map<String, Object>> r = userClient.getUserInfoById(userId);
            if (r == null || r.getData() == null) {
                throw new BusinessException("用户不存在");
            }
            Boolean isDoctor = (Boolean) r.getData().get("isDoctor");
            if (isDoctor == null || !isDoctor) {
                throw new BusinessException("仅认证医师可执行此操作");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("验证医师身份失败: userId={}", userId, e);
            throw new BusinessException("验证医师身份失败");
        }
    }

    /**
     * 通过 Feign 获取用户昵称
     */
    private String getUserName(Long userId) {
        try {
            Result<Map<String, Object>> r = userClient.getUserInfoById(userId);
            if (r != null && r.getData() != null) {
                return (String) r.getData().getOrDefault("nickname", "用户" + userId);
            }
        } catch (Exception e) {
            log.warn("获取用户昵称失败: userId={}", userId, e);
        }
        return "用户" + userId;
    }

    /**
     * 通过 Feign 获取用户头像
     */
    private String getUserAvatar(Long userId) {
        try {
            Result<Map<String, Object>> r = userClient.getUserInfoById(userId);
            if (r != null && r.getData() != null) {
                return (String) r.getData().get("avatar");
            }
        } catch (Exception e) {
            log.warn("获取用户头像失败: userId={}", userId, e);
        }
        return null;
    }

    /**
     * 获取用户医院信息
     */
    private String getUserHospital(Long userId) {
        try {
            Result<Map<String, Object>> r = userClient.getUserInfoById(userId);
            if (r != null && r.getData() != null) {
                return (String) r.getData().get("hospital");
            }
        } catch (Exception e) {
            log.warn("获取用户医院失败: userId={}", userId, e);
        }
        return null;
    }

    /**
     * 获取用户职称
     */
    private String getUserTitle(Long userId) {
        try {
            Result<Map<String, Object>> r = userClient.getUserInfoById(userId);
            if (r != null && r.getData() != null) {
                return (String) r.getData().get("title");
            }
        } catch (Exception e) {
            log.warn("获取用户职称失败: userId={}", userId, e);
        }
        return null;
    }

    /**
     * 转换膳食计划为 DTO
     */
    private DietPlanDTO convertToPlanDTO(DietPlan plan, Long currentUserId) {
        DietPlanDTO dto = new DietPlanDTO();
        dto.setId(plan.getId());
        dto.setDoctorId(plan.getDoctorId());
        dto.setDoctorName(getUserName(plan.getDoctorId()));
        dto.setDoctorAvatar(getUserAvatar(plan.getDoctorId()));
        dto.setDoctorHospital(getUserHospital(plan.getDoctorId()));
        dto.setDoctorTitle(getUserTitle(plan.getDoctorId()));
        dto.setPatientId(plan.getPatientId());
        dto.setPatientName(getUserName(plan.getPatientId()));
        dto.setPatientAvatar(getUserAvatar(plan.getPatientId()));
        dto.setTitle(plan.getTitle());
        dto.setDescription(plan.getDescription());
        dto.setContent(plan.getContent());
        dto.setTargetSymptoms(plan.getTargetSymptoms());
        dto.setDurationDays(plan.getDurationDays());
        dto.setNotes(plan.getNotes());
        dto.setStatus(plan.getStatus());
        dto.setViewCount(plan.getViewCount());
        dto.setCreateTime(plan.getCreateTime());
        dto.setUpdateTime(plan.getUpdateTime());
        return dto;
    }

    /**
     * 转换咨询为 DTO
     */
    private DietConsultationDTO convertToConsultationDTO(DietConsultation consultation, Long currentUserId) {
        DietConsultationDTO dto = new DietConsultationDTO();
        dto.setId(consultation.getId());
        dto.setPatientId(consultation.getPatientId());
        dto.setPatientName(getUserName(consultation.getPatientId()));
        dto.setPatientAvatar(getUserAvatar(consultation.getPatientId()));
        dto.setDoctorId(consultation.getDoctorId());
        dto.setDoctorName(getUserName(consultation.getDoctorId()));
        dto.setDoctorAvatar(getUserAvatar(consultation.getDoctorId()));
        dto.setTitle(consultation.getTitle());
        dto.setContent(consultation.getContent());
        dto.setImages(consultation.getImages());
        dto.setStatus(consultation.getStatus());
        dto.setReplyCount(consultation.getReplyCount());
        dto.setCreateTime(consultation.getCreateTime());
        dto.setUpdateTime(consultation.getUpdateTime());

        // 加载回复列表
        List<ConsultationReply> replies = consultationReplyMapper.selectList(
                new LambdaQueryWrapper<ConsultationReply>()
                        .eq(ConsultationReply::getConsultationId, consultation.getId())
                        .orderByAsc(ConsultationReply::getCreateTime));
        dto.setReplies(replies.stream().map(r -> {
            ConsultationReplyDTO rd = new ConsultationReplyDTO();
            rd.setId(r.getId());
            rd.setConsultationId(r.getConsultationId());
            rd.setUserId(r.getUserId());
            rd.setUserName(getUserName(r.getUserId()));
            rd.setUserAvatar(getUserAvatar(r.getUserId()));
            rd.setContent(r.getContent());
            rd.setImages(r.getImages());
            rd.setIsDoctorReply(r.getIsDoctorReply() != null && r.getIsDoctorReply() == 1);
            rd.setCreateTime(r.getCreateTime());
            return rd;
        }).collect(Collectors.toList()));

        return dto;
    }
}