package com.medifood.message.dto;

import lombok.Data;

@Data
public class ConversationDTO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String lastMessage;
    private String lastTime;
    private Integer unread;
    private Boolean isMutualFollow;
    private Boolean canSend;
    /** 对方是否在线 */
    private Boolean isOnline;
    /** 最后一条消息是否已被对方阅读（仅对"我发的最后一条消息"有效） */
    private Boolean lastMessageRead;
}