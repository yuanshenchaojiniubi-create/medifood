package com.medifood.user.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.ResultCode;
import com.medifood.common.util.JwtUtil;
import com.medifood.common.util.OssUtil;
import com.medifood.user.dto.DoctorCertApplyDTO;
import com.medifood.user.dto.DoctorCertificationDTO;
import com.medifood.user.dto.DoctorCertReviewDTO;
import com.medifood.user.dto.PendingDoctorCertDTO;
import com.medifood.user.dto.PasswordChangeDTO;
import com.medifood.user.dto.UserInfoDTO;
import com.medifood.user.dto.UserLoginDTO;
import com.medifood.user.dto.UserRegisterDTO;
import com.medifood.user.entity.Role;
import com.medifood.user.entity.User;
import com.medifood.user.entity.UserRole;
import com.medifood.user.enums.DoctorCertificationStatus;
import com.medifood.user.mapper.RoleMapper;
import com.medifood.user.mapper.UserMapper;
import com.medifood.user.mapper.UserRoleMapper;
import com.medifood.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 
 * @author MediFood Team
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private OssUtil ossUtil;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(UserRegisterDTO registerDTO) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST);
        }
        
        // 检查邮箱是否已存在
        if (StrUtil.isNotBlank(registerDTO.getEmail())) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, registerDTO.getEmail());
            if (userMapper.selectCount(emailWrapper) > 0) {
                throw new BusinessException("邮箱已被注册");
            }
        }
        
        // 检查手机号是否已存在
        if (StrUtil.isNotBlank(registerDTO.getPhone())) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, registerDTO.getPhone());
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new BusinessException("手机号已被注册");
            }
        }
        
        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        // 使用 BCrypt 加密密码
        user.setPassword(BCrypt.hashpw(registerDTO.getPassword()));
        user.setNickname(StrUtil.isNotBlank(registerDTO.getNickname()) 
                ? registerDTO.getNickname() 
                : registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setIsDoctor(0);
        user.setDoctorCertStatus(DoctorCertificationStatus.UNSUBMITTED.name());
        user.setStatus(1); // 默认启用
        
        userMapper.insert(user);

        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "USER"));
        if (userRole != null) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(userRole.getId());
            userRoleMapper.insert(ur);
        }
        
        log.info("用户注册成功，用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        return user.getId();
    }
    
    @Override
    public User login(UserLoginDTO loginDTO) {
        // 根据账号查找用户（支持用户名、邮箱、手机号登录）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(User::getUsername, loginDTO.getAccount())
                .or().eq(User::getEmail, loginDTO.getAccount())
                .or().eq(User::getPhone, loginDTO.getAccount()));
        
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        
        // 检查账号状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_DISABLED);
        }
        
        // 验证密码
        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        
        // 生成 JWT Token
        /*Map<String, Object> claims = new HashMap<>();
        String token = JwtUtil.generateToken(user.getId(), claims);
        
        log.info("用户登录成功，用户ID: {}, 用户名: {}", user.getId(), user.getUsername());*/
        
        return user;
    }
    
    @Override
    public UserInfoDTO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        
        UserInfoDTO userInfoDTO = new UserInfoDTO();
        BeanUtils.copyProperties(user, userInfoDTO);
        userInfoDTO.setIsDoctor(user.getIsDoctor() != null && user.getIsDoctor() == 1);
        if (StrUtil.isBlank(userInfoDTO.getDoctorCertStatus())) {
            userInfoDTO.setDoctorCertStatus(DoctorCertificationStatus.UNSUBMITTED.name());
        }


        Long roleId = userRoleMapper.selectRoleIdByUserId(userId);
        Role role = roleMapper.selectById(roleId);
        String roleCode = role != null ? role.getRoleCode() : null;
        userInfoDTO.setRoles(roleCode);
        userInfoDTO.setIsAdmin(roleCode != null && roleCode.contains("ADMIN"));

        return userInfoDTO;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UserInfoDTO userInfoDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        
        // 更新用户信息
        if (StrUtil.isNotBlank(userInfoDTO.getNickname())) {
            user.setNickname(userInfoDTO.getNickname());
        }
        if (userInfoDTO.getGender() != null) {
            user.setGender(userInfoDTO.getGender());
        }
        if (userInfoDTO.getBirthday() != null) {
            user.setBirthday(userInfoDTO.getBirthday());
        }
        if (StrUtil.isNotBlank(userInfoDTO.getBio())) {
            user.setBio(userInfoDTO.getBio());
        }
        
        userMapper.updateById(user);
        
        log.info("更新用户信息成功，用户ID: {}", userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        
        log.info("更新用户头像成功，用户ID: {}, 头像URL: {}", userId, avatarUrl);
    }

    @Override
    public DoctorCertificationDTO getDoctorCertificationStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        DoctorCertificationDTO dto = new DoctorCertificationDTO();
        dto.setStatus(StrUtil.isNotBlank(user.getDoctorCertStatus())
                ? user.getDoctorCertStatus()
                : DoctorCertificationStatus.UNSUBMITTED.name());
        dto.setCertificateUrl(user.getDoctorCertFile());
        dto.setDescription(user.getDoctorCertDescription());
        dto.setReason(user.getDoctorCertReason());
        dto.setAppliedAt(user.getDoctorCertAppliedAt());
        dto.setReviewedAt(user.getDoctorCertReviewedAt());
        dto.setDoctor(user.getIsDoctor() != null && user.getIsDoctor() == 1);
        // 回显专业信息
        dto.setHospital(user.getHospital());
        dto.setDepartment(user.getDepartment());
        dto.setTitle(user.getTitle());
        dto.setSpecialties(user.getSpecialties());
        dto.setPracticeYears(user.getPracticeYears());
        return dto;
    }

    /**
     * 提交医师资格认证
     *
     * @param userId 用户ID
     * @param certificateFile 认证材料
     * @param applyInfo 认证申请信息（含专业信息）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyDoctorCertification(Long userId, MultipartFile certificateFile, DoctorCertApplyDTO applyInfo) {
        if (certificateFile == null || certificateFile.isEmpty()) {
            throw new BusinessException("请上传认证材料");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        String status = StrUtil.blankToDefault(user.getDoctorCertStatus(), DoctorCertificationStatus.UNSUBMITTED.name());
        if (DoctorCertificationStatus.APPROVED.name().equals(status) && user.getIsDoctor() != null && user.getIsDoctor() == 1) {
            throw new BusinessException("您已是认证医师");
        }
        if (DoctorCertificationStatus.PENDING.name().equals(status)) {
            throw new BusinessException("您的申请正在审核中，请耐心等待");
        }
        try {
            String fileUrl = ossUtil.upload(certificateFile, "certification");

            user.setDoctorCertFile(fileUrl);
            user.setDoctorCertDescription(applyInfo != null ? applyInfo.getDescription() : null);
            user.setDoctorCertStatus(DoctorCertificationStatus.PENDING.name());
            user.setDoctorCertReason(null);
            user.setDoctorCertAppliedAt(LocalDateTime.now());
            user.setDoctorCertReviewedAt(null);
            user.setIsDoctor(0);
            
            // 保存专业信息
            if (applyInfo != null) {
                user.setHospital(applyInfo.getHospital());
                user.setDepartment(applyInfo.getDepartment());
                user.setTitle(applyInfo.getTitle());
                user.setSpecialties(applyInfo.getSpecialties());
                user.setPracticeYears(applyInfo.getPracticeYears());
            }
            
            userMapper.updateById(user);
            
            log.info("用户提交医师认证，待审核，用户ID: {}", userId);
        } catch (RuntimeException e) {
            log.error("医师认证材料上传失败", e);
            throw new BusinessException("认证材料上传失败，请稍后重试");
        }
    }

    @Override
    public boolean canPublishContent(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        return user.getIsDoctor() != null && user.getIsDoctor() == 1
                && DoctorCertificationStatus.APPROVED.name().equals(StrUtil.blankToDefault(user.getDoctorCertStatus(), ""));
    }

    @Override
    public boolean isAdmin(Long userId) {
        Long RoleID= userRoleMapper.selectRoleIdByUserId(userId);
        String roleCodes= (roleMapper.selectById(RoleID)).getRoleCode();
        //List<String> roles = userRoleMapper.selectRoleCodesByUserId(userId);
        return roleCodes != null && roleCodes.contains("ADMIN");
    }

    @Override
    public List<PendingDoctorCertDTO> listPendingDoctorCertifications(Long adminUserId) {
        assertAdmin(adminUserId);
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getDoctorCertStatus, DoctorCertificationStatus.PENDING.name())
                .orderByDesc(User::getDoctorCertAppliedAt));
        return users.stream().map(u -> {
            PendingDoctorCertDTO d = new PendingDoctorCertDTO();
            d.setUserId(u.getId());
            d.setUsername(u.getUsername());
            d.setNickname(u.getNickname());
            d.setDoctorCertFile(u.getDoctorCertFile());
            d.setDoctorCertDescription(u.getDoctorCertDescription());
            d.setDoctorCertAppliedAt(u.getDoctorCertAppliedAt());
            return d;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewDoctorCertification(Long adminUserId, DoctorCertReviewDTO dto) {
        assertAdmin(adminUserId);
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!DoctorCertificationStatus.PENDING.name().equals(user.getDoctorCertStatus())) {
            throw new BusinessException("该用户当前不在待审核状态");
        }
        if (Boolean.TRUE.equals(dto.getApproved())) {
            user.setDoctorCertStatus(DoctorCertificationStatus.APPROVED.name());
            user.setIsDoctor(1);
            user.setDoctorCertReason(null);
            user.setDoctorCertReviewedAt(LocalDateTime.now());
            
            // 自动分配 DOCTOR 角色
            Role doctorRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "DOCTOR"));
            if (doctorRole != null) {
                Long existingCount = userRoleMapper.selectCount(
                        new LambdaQueryWrapper<UserRole>()
                                .eq(UserRole::getUserId, dto.getUserId())
                                .eq(UserRole::getRoleId, doctorRole.getId()));
                if (existingCount == 0) {
                    UserRole ur = new UserRole();
                    ur.setUserId(dto.getUserId());
                    ur.setRoleId(doctorRole.getId());
                    userRoleMapper.insert(ur);
                    log.info("自动分配DOCTOR角色给用户ID: {}", dto.getUserId());
                }
            }
        } else {
            user.setDoctorCertStatus(DoctorCertificationStatus.REJECTED.name());
            user.setIsDoctor(0);
            user.setDoctorCertReason(StrUtil.blankToDefault(dto.getReason(), "材料不符合要求"));
            user.setDoctorCertReviewedAt(LocalDateTime.now());
        }
        userMapper.updateById(user);
        log.info("管理员 {} 审核用户 {} 医师认证，通过={}", adminUserId, dto.getUserId(), dto.getApproved());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "原密码错误");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "两次输入的新密码不一致");
        }

        if (dto.getNewPassword().length() < 6) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "新密码长度不能少于6位");
        }

        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户 {} 修改密码成功", userId);
    }

    private void assertAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "需要管理员权限");
        }
    }
}

