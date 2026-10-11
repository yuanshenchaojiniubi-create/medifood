package com.medifood.message.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.message.dto.UserInfoDTO;
import com.medifood.message.feign.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserClientFallback implements UserClient {

    @Override
    public Result<UserInfoDTO> getUserInfoById(Long userId) {
        log.warn("获取用户信息降级处理, userId={}", userId);
        return Result.success(null);
    }
}