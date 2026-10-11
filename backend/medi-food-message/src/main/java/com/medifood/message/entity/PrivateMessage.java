package com.medifood.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("private_message")
public class PrivateMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;

    private Long receiverId;

    private String content;

    private String messageType;

    private String imageUrl;

    private Integer isRead;

    private Integer isRecalled;

    private Long quoteMessageId;

    private String quoteContent;

    private Long quoteSenderId;

    private LocalDateTime createdAt;
}