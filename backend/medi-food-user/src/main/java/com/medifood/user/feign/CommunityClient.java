package com.medifood.user.feign;

import com.medifood.common.result.Result;
import com.medifood.user.feign.fallback.CommunityClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(contextId = "userCommunityClient", name = "medi-food-community", path = "/api/community", fallback = CommunityClientFallback.class)
public interface CommunityClient {

    @GetMapping("/user/{userId}/follow-stats")
    Result<Map<String, Object>> getFollowStats(@PathVariable("userId") Long userId,
                                                @RequestParam(required = false) Long currentUserId);
}