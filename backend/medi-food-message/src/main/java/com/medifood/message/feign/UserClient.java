package com.medifood.message.feign;

import com.medifood.common.result.Result;
import com.medifood.message.dto.UserInfoDTO;
import com.medifood.message.feign.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "messageUserClient", name = "medi-food-user", path = "/api/user", fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/{userId}/info")
    Result<UserInfoDTO> getUserInfoById(@PathVariable("userId") Long userId);
}