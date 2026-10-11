package com.medifood.knowledge.feign.fallback;

import com.medifood.common.result.Result;
import com.medifood.knowledge.dto.UserInfoDTO;
import com.medifood.knowledge.feign.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
@Slf4j
@Component("knowledgeUserClientFallback")
public class UserClientFallback implements UserClient {

    @Override
    public Result<UserInfoDTO> getUserInfoById(Long userId) {
        log.error("调用user-service失败，用户ID: {}", userId);
        return Result.fail("用户服务不可用");
    }

    @Override
    public Result<Boolean> canPublishContent(Long userId) {
        log.error("调用user-service校验发布权限失败，用户ID: {}", userId);
        return Result.success(false);
    }

 /*@Override
    public Result<List<UserInfoDTO>> batchGetUsers(List<Long> userIds) {
        System.out.println("调用user-service失败，用户ID数量: {}"+userIds.size());
        return Result.fail("用户服务不可用");
    }

    @Override
    public Result<UserInfoDTO> getCurrentUser(String token) {
        System.out.println("调用user-service失败");
        return Result.fail("用户服务不可用");
    }*/

}

