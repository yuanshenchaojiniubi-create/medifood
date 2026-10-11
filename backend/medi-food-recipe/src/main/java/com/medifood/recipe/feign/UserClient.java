package com.medifood.recipe.feign;

import com.medifood.common.result.Result;
import com.medifood.recipe.dto.UserInfoDTO;
import com.medifood.recipe.feign.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端
 * 
 * @author MediFood Team
 */
@FeignClient(contextId = "recipeUserClient", name = "medi-food-user", path = "/api/user", fallback = UserClientFallback.class)
public interface UserClient {

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/{userId}/info")
    Result<UserInfoDTO> getUserInfoById(@PathVariable("userId") Long userId);

    @GetMapping("/internal/can-publish/{userId}")
    Result<Boolean> canPublishContent(@PathVariable("userId") Long userId);
}
