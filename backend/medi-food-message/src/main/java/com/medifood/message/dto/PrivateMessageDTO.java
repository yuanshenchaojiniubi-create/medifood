package com.medifood.message.dto;

import lombok.Data;

@Data
public class PrivateMessageDTO {
    private Long receiverId;
    private String content;
    private String messageType;
    private String imageUrl;
    private Long quoteMessageId;
}