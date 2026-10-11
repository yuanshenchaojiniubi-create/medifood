package com.medifood.message.dto;

import lombok.Data;

@Data
public class ShareMessageDTO {
    private Long receiverId;
    private String shareType;
    private Long shareId;
    private String shareTitle;
    private String shareImage;
    private String shareUrl;
}