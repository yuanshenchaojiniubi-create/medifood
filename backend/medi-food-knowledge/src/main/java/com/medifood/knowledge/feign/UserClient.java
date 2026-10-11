package com.medifood.knowledge.feign;

import com.medifood.common.result.Result;
import com.medifood.knowledge.dto.UserInfoDTO;
import com.medifood.knowledge.feign.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "knowledgeUserClient", name = "medi-food-user", path = "/api/user",
        fallback = UserClientFallback .class)
public interface UserClient {

    @GetMapping("/{userId}/info")
    Result<UserInfoDTO> getUserInfoById(@PathVariable("userId") Long userId);

    @GetMapping("/internal/can-publish/{userId}")
    Result<Boolean> canPublishContent(@PathVariable("userId") Long userId);

  /*  @PostMapping("/batch")
    Result<List<UserInfoDTO>> batchGetUsers(@RequestBody List<Long> userIds);

    @GetMapping("/current")
    Result<UserInfoDTO> getCurrentUser(@RequestHeader("Authorization") String token);*/
}
