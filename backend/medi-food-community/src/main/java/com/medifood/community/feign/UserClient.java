package com.medifood.community.feign;

import com.medifood.common.result.Result;
import com.medifood.community.dto.UserInfoDTO;
import com.medifood.community.feign.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "communityUserClient", name = "medi-food-user", path = "/api/user", fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/{userId}/info")
    Result<UserInfoDTO> getUserInfoById(@PathVariable("userId") Long userId);

    /** 内部接口：校验用户是否为管理员（内容审核后台权限校验用） */
    @GetMapping("/internal/is-admin/{userId}")
    Result<Boolean> isAdmin(@PathVariable("userId") Long userId);
}