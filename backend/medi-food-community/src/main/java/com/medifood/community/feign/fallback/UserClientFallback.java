package com.medifood.community.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.community.dto.UserInfoDTO;
import com.medifood.community.feign.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("communityUserClientFallback")
public class UserClientFallback implements UserClient {

    @Override
    public Result<UserInfoDTO> getUserInfoById(Long userId) {
        log.error("调用用户服务获取用户信息失败，用户ID: {}", userId);
        return Result.fail("用户服务暂不可用");
    }

    @Override
    public Result<Boolean> isAdmin(Long userId) {
        log.error("调用用户服务校验管理员身份失败，用户ID: {}", userId);
        return Result.fail("用户服务暂不可用");
    }
}