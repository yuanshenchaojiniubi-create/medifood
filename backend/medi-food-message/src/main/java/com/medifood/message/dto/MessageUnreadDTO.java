package com.medifood.message.dto;

import lombok.Data;

@Data
public class MessageUnreadDTO {
    private Long chatUnread;
    private Long totalUnread;
    private Long notifyUnread;
}