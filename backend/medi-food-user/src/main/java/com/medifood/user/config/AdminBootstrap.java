package com.medifood.user.config;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.user.entity.Role;
import com.medifood.user.entity.User;
import com.medifood.user.entity.UserRole;
import com.medifood.user.enums.DoctorCertificationStatus;
import com.medifood.user.mapper.RoleMapper;
import com.medifood.user.mapper.UserMapper;
import com.medifood.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 若库中不存在任何管理员账号，则创建默认管理员（仅首次启动生效）。
 * <p>默认账号 admin / admin123 仅供本地开发调试；生产环境必须通过
 * {@code medifood.admin.default-password} 配置自定义密码，否则启动日志会输出醒目警告。</p>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String INSECURE_DEFAULT_PASS = "admin123";

    /** 默认管理员密码：可通过配置覆盖，不配置则沿用 admin123（仅限本地调试） */
    @Value("${medifood.admin.default-password:admin123}")
    private String defaultAdminPass;

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public void run(ApplicationArguments args) {
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "ADMIN"));
        if (adminRole == null) {
            log.warn("role 表缺少 ADMIN 角色，跳过默认管理员初始化");
            return;
        }
        long adminBindings = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()));
        if (adminBindings > 0) {
            return;
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, DEFAULT_ADMIN_USER)) > 0) {
            log.warn("已存在用户名为 {} 的账号，跳过默认管理员初始化（请手动绑定 ADMIN 角色）", DEFAULT_ADMIN_USER);
            return;
        }
        User u = new User();
        u.setUsername(DEFAULT_ADMIN_USER);
        u.setPassword(BCrypt.hashpw(defaultAdminPass, BCrypt.gensalt()));
        u.setNickname("系统管理员");
        u.setIsDoctor(0);
        u.setDoctorCertStatus(DoctorCertificationStatus.UNSUBMITTED.name());
        u.setStatus(1);
        userMapper.insert(u);

        UserRole ur = new UserRole();
        ur.setUserId(u.getId());
        ur.setRoleId(adminRole.getId());
        userRoleMapper.insert(ur);

        if (INSECURE_DEFAULT_PASS.equals(defaultAdminPass)) {
            log.warn("已创建默认管理员账号: {} / {} — 使用了不安全的默认密码！"
                    + "生产环境必须通过 medifood.admin.default-password 配置自定义密码并立即修改", DEFAULT_ADMIN_USER, defaultAdminPass);
        } else {
            log.info("已创建默认管理员账号: {}（密码使用自定义配置）", DEFAULT_ADMIN_USER);
        }
    }
}
