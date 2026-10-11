package com.medifood.doctor.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.doctor.feign.UserClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户服务 Feign 降级
 *
 * @author MediFood Team
 */
@Component
public class UserClientFallback implements UserClient {

    @Override
    public Result<Map<String, Object>> getUserInfoById(Long userId) {
        return Result.success(null);
    }
}