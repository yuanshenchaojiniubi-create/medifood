package com.medifoodai.client.fallback;

import com.medifood.common.result.Result;
import com.medifoodai.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务 Feign 调用降级处理
 * <p>
 * 当 User 服务不可用时，返回空数据，不影响 AI 核心问答功能
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Result<Map<String, Object>> getUserInfoById(Long userId) {
        log.warn("用户服务调用降级: userId={}, 返回空数据", userId);
        Map<String, Object> emptyData = new HashMap<>();
        emptyData.put("id", userId);
        emptyData.put("nickname", "用户" + userId);
        return Result.success(emptyData);
    }
}