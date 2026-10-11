package com.medifood.message.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请 DTO（含用户信息）
 */
@Data
public class FriendRequestDTO {
    private Long id;
    private Long fromUserId;
    private String fromUserName;
    private String fromUserAvatar;
    private Long toUserId;
    private String toUserName;
    private String toUserAvatar;
    private String message;
    private String status;
    private LocalDateTime createTime;
}