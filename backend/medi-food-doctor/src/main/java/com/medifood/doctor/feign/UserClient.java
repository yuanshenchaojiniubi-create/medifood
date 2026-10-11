package com.medifood.doctor.feign;

import com.medifood.common.result.Result;
import com.medifood.doctor.feign.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 用户服务 Feign 客户端
 *
 * @author MediFood Team
 */
@FeignClient(contextId = "doctorUserClient", name = "medi-food-user", path = "/api/user", fallback = UserClientFallback.class)
public interface UserClient {

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/{userId}/info")
    Result<Map<String, Object>> getUserInfoById(@PathVariable("userId") Long userId);
}