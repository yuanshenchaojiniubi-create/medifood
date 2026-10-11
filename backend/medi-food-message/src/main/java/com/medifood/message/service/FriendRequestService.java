package com.medifood.message.service;

import com.medifood.message.dto.FriendRequestDTO;
import com.medifood.message.entity.FriendRequest;

import java.util.List;
import java.util.Map;

public interface FriendRequestService {

    /**
     * 发起好友申请
     */
    FriendRequest sendRequest(Long fromUserId, Long toUserId, String message);

    /**
     * 同意好友申请
     */
    void acceptRequest(Long userId, Long requestId);

    /**
     * 拒绝好友申请
     */
    void rejectRequest(Long userId, Long requestId);

    /**
     * 获取我收到的待处理申请列表
     */
    List<FriendRequestDTO> getPendingRequests(Long userId);

    /**
     * 获取我发出的申请列表
     */
    List<FriendRequestDTO> getSentRequests(Long userId);

    /**
     * 查询两个用户之间的好友申请状态
     */
    Map<String, Object> getRequestStatus(Long userId, Long peerUserId);
}