package com.medifood.user.controller;

import com.medifood.common.result.Result;
import com.medifood.common.util.JwtUtils;
import com.medifood.user.dto.DoctorCertApplyDTO;
import com.medifood.user.dto.DoctorCertificationDTO;
import com.medifood.user.dto.PasswordChangeDTO;
import com.medifood.user.dto.UserInfoDTO;
import com.medifood.user.dto.UserLoginDTO;
import com.medifood.user.dto.UserRegisterDTO;
import com.medifood.user.entity.Banner;
import com.medifood.user.entity.User;
import com.medifood.user.feign.CommunityClient;
import com.medifood.user.service.BannerService;
import com.medifood.user.service.UserService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户控制器
 * 
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class
UserController {
    
    @Autowired
    private UserService userService;
    @Autowired
    private  JwtUtils jwtUtils;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CommunityClient communityClient;

    @Autowired
    private BannerService bannerService;
    /**
     * 用户注册
     */
    @SentinelResource(value = "user_register", blockHandler = "handleRegisterBlock")
    @PostMapping("/register")
    public Result<Long> register(@Validated @RequestBody UserRegisterDTO registerDTO) {
        Long userId = userService.register(registerDTO);
        return Result.success("注册成功", userId);
    }
    
    /**
     * 用户登录
     */
    @SentinelResource(value = "user_login", blockHandler = "handleLoginBlock")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody UserLoginDTO loginDTO, HttpServletResponse response) {
        System.out.println("到达这里");
        User user = userService.login(loginDTO);
        // 2. 生成 Access Token
        String accessToken = jwtUtils.generateAccessToken(String.valueOf(user.getId()), user.getUsername());

        // 3. 生成 Refresh Token（随机字符串）
        String refreshToken = jwtUtils.generateRefreshToken(String.valueOf(user.getId()), user.getUsername()); // 或 UUID

        // 4. 将 Refresh Token 存入 Redis，关联用户
        redisTemplate.opsForValue().set(
                "refresh_token:" + user.getId(),
                refreshToken,
                604800,
                TimeUnit.SECONDS
        );
        // 5. 构造返回给前端的 R 对象（只含 Access Token 和 expiresIn）
       Map<String, Object> data = new HashMap<>();
       data.put("accessToken", accessToken);
       data.put("expiresIn", jwtUtils.getAccessExpiration() );
        // 6. 构造 Refresh Token 的 HttpOnly + Secure Cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)                     // 禁止 JS 访问
                .secure(false)                       // 仅 HTTPS 传输
                .sameSite("Strict")                 // 仅同站发送，防 CSRF
                .path("/")                          // 可根据需要设置，如 "/auth/refresh"
                .maxAge(jwtUtils.getRefreshExpiration() ) // 单位秒
                .build();

        // 7. 将 Cookie 加入响应头
        response.addHeader("Set-Cookie", cookie.toString());

        // 8. 返回 R 对象（含 accessToken 和 expiresIn）
        return Result.success("登录成功", data);
    }
    
    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoDTO> getUserInfo(HttpServletRequest request) {
        // X-User-Id 由网关 JWT 校验通过后写入（网关已剥离客户端伪造的同名头）
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        UserInfoDTO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    public Result<?> updateUserInfo(HttpServletRequest request,
                                    @Validated @RequestBody UserInfoDTO userInfoDTO) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        userService.updateUserInfo(userId, userInfoDTO);
        return Result.success("更新成功");
    }
    
    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public Result<?> updateAvatar(HttpServletRequest request,
                                  @RequestParam("avatarUrl") String avatarUrl) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        userService.updateAvatar(userId, avatarUrl);
        return Result.success("头像更新成功");
    }

    @GetMapping("/banners")
    public Result<List<Banner>> getBanners() {
        return Result.success(bannerService.listActive());
    }

    /**
     * 内部接口：判断用户是否可发布知识/食谱（供其他微服务 Feign 调用，不鉴权）
     */
    @GetMapping("/internal/can-publish/{userId}")
    public Result<Boolean> canPublishContent(@PathVariable("userId") Long userId) {
        return Result.success(userService.canPublishContent(userId));
    }

    /**
     * 内部接口：校验用户是否为管理员（供内容审核等模块的 Feign 调用，不走网关公网）
     */
    @GetMapping("/internal/is-admin/{userId}")
    public Result<Boolean> isAdmin(@PathVariable("userId") Long userId) {
        return Result.success(userService.isAdmin(userId));
    }

    /**
     * 获取指定用户信息（他人主页）
     * <p>隐私保护：查看他人主页时对手机号/邮箱脱敏、隐藏生日与审核信息，
     * 防止遍历 userId 批量爬取用户联系方式。</p>
     */
    @GetMapping("/{userId}/info")
    public Result<UserInfoDTO> getUserInfoById(@PathVariable("userId") Long userId,
                                                HttpServletRequest request) {
        // 当前登录用户从网关注入的身份头读取（可信来源，前端传参不可信）
        Long currentUserId = Long.valueOf(request.getHeader("X-User-Id"));

        UserInfoDTO userInfo = userService.getUserInfo(userId);

        // 仅本人可见完整隐私字段，他人访问一律脱敏
        if (!userId.equals(currentUserId)) {
            maskSensitiveFields(userInfo);
        }

        try {
            Result<Map<String, Object>> statsResult = communityClient.getFollowStats(userId, currentUserId);
            if (statsResult != null && statsResult.getCode() == 200 && statsResult.getData() != null) {
                Map<String, Object> stats = statsResult.getData();
                userInfo.setFollowingCount(stats.get("followingCount") != null ? ((Number) stats.get("followingCount")).intValue() : 0);
                userInfo.setFollowersCount(stats.get("followersCount") != null ? ((Number) stats.get("followersCount")).intValue() : 0);
                userInfo.setMutualCount(stats.get("mutualCount") != null ? ((Number) stats.get("mutualCount")).intValue() : 0);
                userInfo.setIsFollowing(stats.get("isFollowing") != null && (Boolean) stats.get("isFollowing"));
            }
        } catch (Exception e) {
            log.error("获取关注统计失败, userId: {}", userId, e);
        }

        return Result.success(userInfo);
    }

    /**
     * 隐私字段脱敏：他人视角下手机号/邮箱打码，生日与审核信息不返回
     */
    private void maskSensitiveFields(UserInfoDTO dto) {
        if (dto == null) {
            return;
        }
        // 手机号：138****5678
        if (dto.getPhone() != null && dto.getPhone().length() >= 7) {
            dto.setPhone(dto.getPhone().substring(0, 3) + "****"
                    + dto.getPhone().substring(dto.getPhone().length() - 4));
        }
        // 邮箱：a***@domain.com
        if (dto.getEmail() != null && dto.getEmail().contains("@")) {
            int at = dto.getEmail().indexOf('@');
            String name = dto.getEmail().substring(0, at);
            dto.setEmail((name.length() > 0 ? name.charAt(0) + "***" : "***") + dto.getEmail().substring(at));
        }
        // 生日与审核相关字段属于个人隐私，他人视角直接隐藏
        dto.setBirthday(null);
        dto.setDoctorCertReason(null);
        dto.setDoctorCertDescription(null);
    }

    /**
     * 获取医师资格认证状态
     */
    @GetMapping("/certification/doctor/status")
    public Result<DoctorCertificationDTO> getDoctorCertificationStatus(HttpServletRequest request) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        DoctorCertificationDTO dto = userService.getDoctorCertificationStatus(userId);
        return Result.success(dto);
    }

    /**
     * 申请医师资格认证
     */
    @PostMapping("/certification/doctor")
    public Result<?> applyDoctorCertification(HttpServletRequest request,
                                              @RequestPart("file") MultipartFile certificateFile,
                                              @RequestPart(value = "applyInfo", required = false) DoctorCertApplyDTO applyInfo) {
        Long userId = Long.valueOf(request.getHeader("X-User-Id"));
        userService.applyDoctorCertification(userId, certificateFile, applyInfo);
        return Result.success("认证申请已提交，请等待管理员审核");
    }


    /*刷新 Access Token*/
    @SentinelResource(value = "user_refresh", blockHandler = "handleRefreshBlock")
    @PostMapping("/refresh")
    public Result refreshAccessToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 1. 从 Cookie 中获取 refreshToken
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return ResponseEntity.status(401).body(Result.fail("未提供 Refresh Token")).getBody();
        }

        // 2. 解析 refreshToken，获取用户信息
        String userId;
        String username;
        try {
            // 可能抛出 ExpiredJwtException（如果 Refresh Token 也设置了过期时间并过期了）
            var claims = jwtUtils.parseToken(refreshToken);
            userId = jwtUtils.getUserIdFromToken(refreshToken);
            username = jwtUtils.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Result.fail("无效的 Refresh Token")).getBody();
        }

        // 3. 校验 Refresh Token 是否存在于 Redis 中（可选但强烈推荐）
        String redisKey = "refresh_token:" + userId;
        String validRefreshTokenFromRedis = redisTemplate.opsForValue().get(redisKey);

       /* if (validRefreshTokenFromRedis == null || !validRefreshTokenFromRedis.equals(refreshToken)) {
            return ResponseEntity.status(401).body(Result.fail("Refresh Token 已失效或被撤销")).getBody();
        }*/
        // 更清晰的判断逻辑
        if (validRefreshTokenFromRedis == null) {
            return Result.fail("Refresh Token 已失效");
        }

        if (!validRefreshTokenFromRedis.equals(refreshToken)) {
            return Result.fail("Refresh Token 不匹配，可能已被替换");
        }
        // 4. ✅ Refresh Token 有效，生成新的 Access Token
        String newAccessToken = jwtUtils.generateAccessToken(userId, username);

        // 5. （可选）生成新的 Refresh Token，实现轮换，旧的失效
        // 如果启用轮换，需要删除旧的，存入新的
        /*
        String newRefreshToken = jwtUtils.generateRefreshToken(userId, username);
        redisTemplate.opsForValue().set(
            redisKey,
            newRefreshToken,
            jwtUtils.getRefreshExpiration(), // 比如 7 天
            TimeUnit.SECONDS
        );
        */

        // 6. 返回新的 Access Token 给客户端
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        data.put("expiresIn", jwtUtils.getAccessExpiration());
       /* Result result = Result.success()
                .put("accessToken", newAccessToken)
                .put("expiresIn", jwtUtils.getAccessExpiration()); */// 单位：秒

        // 7. （可选）如果启用了 Refresh Token 轮换，也可以将新的 refreshToken 通过 Cookie 返回
        /*
        String newRefreshToken = ...;
        ResponseCookie newCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        response.addHeader("Set-Cookie", newCookie.toString());
        */

        return Result.success( data);
    }

    /**
     * 从 Cookie 中提取 Refresh Token（根据你的前端设置）
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 修改密码
     */
    @PostMapping("/password")
    public Result<?> changePassword(
            @Validated @RequestBody PasswordChangeDTO dto,
            @RequestAttribute("userId") Long userId) {
        userService.changePassword(userId, dto);
        return Result.success("密码修改成功");
    }

    // ========== Sentinel 限流降级处理 ==========

    /**
     * 处理 Sentinel 限流/熔断降级。
     * 注意：blockHandler 的方法签名必须为「被限流方法的参数 + BlockException」，
     * 原实现用 Object... 变参导致 Sentinel 匹配不到，限流时异常透传成 500。
     */
    public Result<?> handleRegisterBlock(UserRegisterDTO registerDTO, BlockException e) {
        log.warn("注册接口被 Sentinel 拦截: resource={}", e.getRule().getResource());
        return Result.fail(429, "注册请求过于频繁，请稍后再试（安全限流）");
    }

    public Result<?> handleLoginBlock(UserLoginDTO loginDTO, HttpServletResponse response, BlockException e) {
        log.warn("登录接口被 Sentinel 拦截: resource={}", e.getRule().getResource());
        return Result.fail(429, "登录请求过于频繁，请 1 秒后再试（安全限流）");
    }

    public Result<?> handleRefreshBlock(HttpServletRequest request, HttpServletResponse response, BlockException e) {
        log.warn("刷新 Token 接口被 Sentinel 拦截: resource={}", e.getRule().getResource());
        return Result.fail(429, "请求过于频繁，请稍后再试（限流保护）");
    }
}

