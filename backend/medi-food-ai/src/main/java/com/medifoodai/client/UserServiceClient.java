package com.medifoodai.client;

import com.medifood.common.result.Result;
import com.medifoodai.client.fallback.UserServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 用户服务 Feign 客户端
 * <p>
 * 用于获取用户画像信息（昵称、性别、体质倾向等），
 * 支持熔断降级（fallback）
 * </p>
 *
 * @author MediFood Team
 */
@FeignClient(contextId = "aiUserClient",
             name = "medi-food-user",
             path = "/api/user",
             fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息 Map（包含 nickname, avatar, gender, constitution 等字段）
     */
    @GetMapping("/{userId}/info")
    Result<Map<String, Object>> getUserInfoById(@PathVariable("userId") Long userId);
}