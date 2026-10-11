package com.medifood.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请实体
 * <p>
 * 用户A发起好友申请 → 用户B同意/拒绝 → 双方自动互关
 * </p>
 */
@Data
@TableName("friend_request")
public class FriendRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起申请的用户ID */
    private Long fromUserId;

    /** 接收申请的用户ID */
    private Long toUserId;

    /** 申请附言 */
    private String message;

    /**
     * 申请状态
     * PENDING - 待处理
     * ACCEPTED - 已同意
     * REJECTED - 已拒绝
     */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}