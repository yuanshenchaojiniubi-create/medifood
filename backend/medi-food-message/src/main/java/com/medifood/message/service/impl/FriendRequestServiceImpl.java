package com.medifood.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifood.message.dto.FriendRequestDTO;
import com.medifood.message.dto.UserInfoDTO;
import com.medifood.message.entity.FriendRequest;
import com.medifood.message.entity.UserFollow;
import com.medifood.message.feign.UserClient;
import com.medifood.message.mapper.FriendRequestMapper;
import com.medifood.message.mapper.UserFollowMapper;
import com.medifood.message.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendRequestServiceImpl implements FriendRequestService {

    private final FriendRequestMapper requestMapper;
    private final UserFollowMapper followMapper;
    private final UserClient userClient;

    @Override
    @Transactional
    public FriendRequest sendRequest(Long fromUserId, Long toUserId, String message) {
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException("不能给自己发好友申请哦");
        }

        // 检查是否已经是好友（互关）
        if (isMutualFollow(fromUserId, toUserId)) {
            throw new BusinessException("你们已经是好友了");
        }

        // 检查是否有待处理的申请（防止重复申请）
        FriendRequest existing = requestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUserId, fromUserId)
                        .eq(FriendRequest::getToUserId, toUserId)
                        .eq(FriendRequest::getStatus, "PENDING"));
        if (existing != null) {
            throw new BusinessException("你已经发送过好友申请了，请等待对方处理");
        }

        // 对方是否也向我发过申请（双向申请自动成为好友）
        FriendRequest reverse = requestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUserId, toUserId)
                        .eq(FriendRequest::getToUserId, fromUserId)
                        .eq(FriendRequest::getStatus, "PENDING"));
        if (reverse != null) {
            // 双向申请 → 自动成为好友
            reverse.setStatus("ACCEPTED");
            reverse.setUpdateTime(LocalDateTime.now());
            requestMapper.updateById(reverse);
            createMutualFollow(fromUserId, toUserId);
            log.info("双向申请自动成为好友: {} <-> {}", fromUserId, toUserId);
            return reverse;
        }

        FriendRequest req = new FriendRequest();
        req.setFromUserId(fromUserId);
        req.setToUserId(toUserId);
        req.setMessage(message != null ? message : "我想和你成为好友");
        req.setStatus("PENDING");
        req.setCreateTime(LocalDateTime.now());
        requestMapper.insert(req);
        log.info("好友申请已发送: {} -> {}", fromUserId, toUserId);
        return req;
    }

    @Override
    @Transactional
    public void acceptRequest(Long userId, Long requestId) {
        FriendRequest req = requestMapper.selectById(requestId);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (!req.getToUserId().equals(userId)) {
            throw new BusinessException("无权操作此申请");
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("该申请已被处理");
        }
        req.setStatus("ACCEPTED");
        req.setUpdateTime(LocalDateTime.now());
        requestMapper.updateById(req);

        // 自动互关
        createMutualFollow(req.getFromUserId(), req.getToUserId());
        log.info("好友申请已同意: {} <-> {}", req.getFromUserId(), req.getToUserId());
    }

    @Override
    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        FriendRequest req = requestMapper.selectById(requestId);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (!req.getToUserId().equals(userId)) {
            throw new BusinessException("无权操作此申请");
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("该申请已被处理");
        }
        req.setStatus("REJECTED");
        req.setUpdateTime(LocalDateTime.now());
        requestMapper.updateById(req);
        log.info("好友申请已拒绝: {} -> {}", req.getFromUserId(), req.getToUserId());
    }

    @Override
    public List<FriendRequestDTO> getPendingRequests(Long userId) {
        List<FriendRequest> requests = requestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getToUserId, userId)
                        .eq(FriendRequest::getStatus, "PENDING")
                        .orderByDesc(FriendRequest::getCreateTime));
        return toDTOList(requests, userId);
    }

    @Override
    public List<FriendRequestDTO> getSentRequests(Long userId) {
        List<FriendRequest> requests = requestMapper.selectList(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUserId, userId)
                        .orderByDesc(FriendRequest::getCreateTime));
        return toDTOList(requests, userId);
    }

    @Override
    public Map<String, Object> getRequestStatus(Long userId, Long peerUserId) {
        Map<String, Object> result = new HashMap<>();
        result.put("isMutualFollow", isMutualFollow(userId, peerUserId));

        // 我发给对方的申请
        FriendRequest myRequest = requestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUserId, userId)
                        .eq(FriendRequest::getToUserId, peerUserId)
                        .orderByDesc(FriendRequest::getCreateTime)
                        .last("LIMIT 1"));
        if (myRequest != null) {
            result.put("myRequestStatus", myRequest.getStatus());
            result.put("myRequestId", myRequest.getId());
        }

        // 对方发给我的申请
        FriendRequest peerRequest = requestMapper.selectOne(
                new LambdaQueryWrapper<FriendRequest>()
                        .eq(FriendRequest::getFromUserId, peerUserId)
                        .eq(FriendRequest::getToUserId, userId)
                        .orderByDesc(FriendRequest::getCreateTime)
                        .last("LIMIT 1"));
        if (peerRequest != null) {
            result.put("peerRequestStatus", peerRequest.getStatus());
            result.put("peerRequestId", peerRequest.getId());
        }

        return result;
    }

    private List<FriendRequestDTO> toDTOList(List<FriendRequest> requests, Long currentUserId) {
        // 收集所有需要查询的用户ID
        Set<Long> userIds = new HashSet<>();
        for (FriendRequest r : requests) {
            userIds.add(r.getFromUserId());
            userIds.add(r.getToUserId());
        }

        // 批量获取用户信息
        Map<Long, UserInfoDTO> userMap = new HashMap<>();
        for (Long uid : userIds) {
            try {
                Result<UserInfoDTO> r = userClient.getUserInfoById(uid);
                if (r != null && r.getCode() == 200 && r.getData() != null) {
                    userMap.put(uid, r.getData());
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败: userId={}", uid);
            }
        }

        return requests.stream().map(r -> {
            FriendRequestDTO dto = new FriendRequestDTO();
            dto.setId(r.getId());
            dto.setFromUserId(r.getFromUserId());
            dto.setToUserId(r.getToUserId());
            dto.setMessage(r.getMessage());
            dto.setStatus(r.getStatus());
            dto.setCreateTime(r.getCreateTime());

            UserInfoDTO fromUser = userMap.get(r.getFromUserId());
            if (fromUser != null) {
                dto.setFromUserName(fromUser.getNickname() != null ? fromUser.getNickname() : fromUser.getUsername());
                dto.setFromUserAvatar(fromUser.getAvatar());
            }
            UserInfoDTO toUser = userMap.get(r.getToUserId());
            if (toUser != null) {
                dto.setToUserName(toUser.getNickname() != null ? toUser.getNickname() : toUser.getUsername());
                dto.setToUserAvatar(toUser.getAvatar());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private boolean isMutualFollow(Long userId1, Long userId2) {
        return checkFollowExists(userId1, userId2) && checkFollowExists(userId2, userId1);
    }

    private boolean checkFollowExists(Long followerId, Long followingId) {
        Long count = followMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFollowingId, followingId));
        return count != null && count > 0;
    }

    private void createMutualFollow(Long userId1, Long userId2) {
        if (!checkFollowExists(userId1, userId2)) {
            UserFollow f1 = new UserFollow();
            f1.setFollowerId(userId1);
            f1.setFollowingId(userId2);
            f1.setCreateTime(LocalDateTime.now());
            followMapper.insert(f1);
        }
        if (!checkFollowExists(userId2, userId1)) {
            UserFollow f2 = new UserFollow();
            f2.setFollowerId(userId2);
            f2.setFollowingId(userId1);
            f2.setCreateTime(LocalDateTime.now());
            followMapper.insert(f2);
        }
    }
}