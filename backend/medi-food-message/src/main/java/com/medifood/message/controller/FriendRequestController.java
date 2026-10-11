package com.medifood.message.controller;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.message.dto.FriendRequestDTO;
import com.medifood.message.entity.FriendRequest;
import com.medifood.message.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友申请控制器
 * <p>
 * 提供好友申请的发送、同意、拒绝、查询等功能。
 * 替代原有的"只能发1条消息"的硬性限制，让用户通过申请-同意的流程建立好友关系。
 * </p>
 */
@RestController
@RequestMapping("/api/message/friend-request")
@RequiredArgsConstructor
@Slf4j
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    /**
     * 发送好友申请
     */
    @PostMapping("/send")
    public Result<FriendRequest> sendRequest(
            @CurrentUser Long userId,
            @RequestBody Map<String, Object> body) {
        Long toUserId = Long.valueOf(body.get("toUserId").toString());
        String message = (String) body.getOrDefault("message", "我想和你成为好友");
        return Result.success(friendRequestService.sendRequest(userId, toUserId, message));
    }

    /**
     * 同意好友申请
     */
    @PostMapping("/accept/{requestId}")
    public Result<?> acceptRequest(@CurrentUser Long userId, @PathVariable Long requestId) {
        friendRequestService.acceptRequest(userId, requestId);
        return Result.success();
    }

    /**
     * 拒绝好友申请
     */
    @PostMapping("/reject/{requestId}")
    public Result<?> rejectRequest(@CurrentUser Long userId, @PathVariable Long requestId) {
        friendRequestService.rejectRequest(userId, requestId);
        return Result.success();
    }

    /**
     * 获取我收到的待处理申请
     */
    @GetMapping("/pending")
    public Result<List<FriendRequestDTO>> getPendingRequests(@CurrentUser Long userId) {
        return Result.success(friendRequestService.getPendingRequests(userId));
    }

    /**
     * 获取我发出的申请
     */
    @GetMapping("/sent")
    public Result<List<FriendRequestDTO>> getSentRequests(@CurrentUser Long userId) {
        return Result.success(friendRequestService.getSentRequests(userId));
    }

    /**
     * 查询与某用户的好友申请状态
     */
    @GetMapping("/status/{peerUserId}")
    public Result<Map<String, Object>> getRequestStatus(
            @CurrentUser Long userId,
            @PathVariable Long peerUserId) {
        return Result.success(friendRequestService.getRequestStatus(userId, peerUserId));
    }
}