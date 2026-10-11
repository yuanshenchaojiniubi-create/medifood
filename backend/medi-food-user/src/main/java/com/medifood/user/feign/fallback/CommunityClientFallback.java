package com.medifood.user.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.user.feign.CommunityClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component("userCommunityClientFallback")
public class CommunityClientFallback implements CommunityClient {

    @Override
    public Result<Map<String, Object>> getFollowStats(Long userId, Long currentUserId) {
        log.error("调用社区服务获取关注统计失败，用户ID: {}", userId);
        return Result.fail("社区服务暂不可用");
    }
}