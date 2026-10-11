package com.medifood.recipe.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.recipe.dto.UserInfoDTO;
import com.medifood.recipe.feign.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Feign 客户端降级处理类
 * 
 * @author MediFood Team
 */
@Slf4j
@Component("recipeUserClientFallback")
public class UserClientFallback implements UserClient {

    @Override
    public Result<UserInfoDTO> getUserInfoById(Long userId) {
        log.error("调用用户服务获取用户信息失败，用户ID: {}", userId);
        return Result.fail("用户服务暂不可用");
    }

    @Override
    public Result<Boolean> canPublishContent(Long userId) {
        log.error("调用用户服务校验发布权限失败，用户ID: {}", userId);
        return Result.success(false);
    }
}
