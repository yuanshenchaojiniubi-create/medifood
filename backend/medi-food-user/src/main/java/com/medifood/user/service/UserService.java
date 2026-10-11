package com.medifood.user.service;

import com.medifood.user.dto.DoctorCertApplyDTO;
import com.medifood.user.dto.DoctorCertificationDTO;
import com.medifood.user.dto.DoctorCertReviewDTO;
import com.medifood.user.dto.PendingDoctorCertDTO;
import com.medifood.user.dto.PasswordChangeDTO;
import com.medifood.user.dto.UserInfoDTO;
import com.medifood.user.dto.UserLoginDTO;
import com.medifood.user.dto.UserRegisterDTO;
import com.medifood.user.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口
 * 
 * @author MediFood Team
 */
public interface UserService {
    
    /**
     * 用户注册
     * 
     * @param registerDTO 注册信息
     * @return 用户ID
     */
    Long register(UserRegisterDTO registerDTO);
    
    /**
     * 用户登录
     * 
     * @param loginDTO 登录信息
     * @return JWT Token
     */
    User login(UserLoginDTO loginDTO);
    
    /**
     * 获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfoDTO getUserInfo(Long userId);
    
    /**
     * 更新用户信息
     * 
     * @param userId 用户ID
     * @param userInfoDTO 用户信息
     */
    void updateUserInfo(Long userId, UserInfoDTO userInfoDTO);
    
    /**
     * 上传头像
     * 
     * @param userId 用户ID
     * @param avatarUrl 头像URL
     */
    void updateAvatar(Long userId, String avatarUrl);

    /**
     * 获取医师资格认证状态
     *
     * @param userId 用户ID
     * @return 医师认证信息
     */
    DoctorCertificationDTO getDoctorCertificationStatus(Long userId);

    /**
     * 提交医师资格认证
     *
     * @param userId 用户ID
     * @param certificateFile 认证材料
     * @param applyInfo 认证申请信息（含专业信息）
     */
    void applyDoctorCertification(Long userId, MultipartFile certificateFile, DoctorCertApplyDTO applyInfo);

    /**
     * 是否具备发布养生知识/食谱资格（认证医师且已通过审核）
     */
    boolean canPublishContent(Long userId);

    /**
     * 是否为管理员
     */
    boolean isAdmin(Long userId);

    /**
     * 待审核医师认证列表
     */
    List<PendingDoctorCertDTO> listPendingDoctorCertifications(Long adminUserId);

    /**
     * 管理员审核医师认证
     */
    void reviewDoctorCertification(Long adminUserId, DoctorCertReviewDTO dto);

    /**
     * 修改密码
     *
     * @param userId 用户ID
     * @param dto 新旧密码
     */
    void changePassword(Long userId, PasswordChangeDTO dto);
}

