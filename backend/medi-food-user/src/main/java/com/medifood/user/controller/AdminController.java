package com.medifood.user.controller;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.user.dto.DoctorCertReviewDTO;
import com.medifood.user.dto.PendingDoctorCertDTO;
import com.medifood.user.entity.Banner;
import com.medifood.user.service.BannerService;
import com.medifood.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员接口（需管理员角色）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final BannerService bannerService;

    @GetMapping("/doctor-certifications/pending")
    public Result<List<PendingDoctorCertDTO>> listPendingDoctorCerts(@CurrentUser Long adminUserId) {
        return Result.success(userService.listPendingDoctorCertifications(adminUserId));
    }

    @PostMapping("/doctor-certifications/review")
    public Result<?> reviewDoctorCert(@CurrentUser Long adminUserId,
                                      @Valid @RequestBody DoctorCertReviewDTO dto) {
        userService.reviewDoctorCertification(adminUserId, dto);
        return Result.success("审核已处理");
    }

    @GetMapping("/banners")
    public Result<List<Banner>> listBanners(@CurrentUser Long adminUserId) {
        userService.isAdmin(adminUserId);
        return Result.success(bannerService.listAll());
    }

    @PostMapping("/banner")
    public Result<Banner> createBanner(@CurrentUser Long adminUserId,
                                       @RequestBody Banner banner) {
        userService.isAdmin(adminUserId);
        return Result.success(bannerService.create(banner));
    }

    @PutMapping("/banner/{id}")
    public Result<Banner> updateBanner(@CurrentUser Long adminUserId,
                                       @PathVariable Long id,
                                       @RequestBody Banner banner) {
        userService.isAdmin(adminUserId);
        return Result.success(bannerService.update(id, banner));
    }

    @DeleteMapping("/banner/{id}")
    public Result<?> deleteBanner(@CurrentUser Long adminUserId,
                                  @PathVariable Long id) {
        userService.isAdmin(adminUserId);
        bannerService.delete(id);
        return Result.success("已删除");
    }
}
