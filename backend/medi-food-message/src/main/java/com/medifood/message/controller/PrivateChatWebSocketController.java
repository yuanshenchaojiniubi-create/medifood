package com.medifood.message.controller;

import com.medifood.message.dto.PrivateMessageDTO;
import com.medifood.message.entity.PrivateMessage;
import com.medifood.message.service.OnlineStatusService;
import com.medifood.message.service.PrivateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PrivateChatWebSocketController {

    private final PrivateMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OnlineStatusService onlineStatusService;

    @MessageMapping("/chat.private.send")
    public void sendMessage(@Payload PrivateMessageDTO dto, Principal principal) {
        Long senderId = Long.valueOf(principal.getName());

        PrivateMessage saved = messageService.send(senderId, dto);

        Map<String, Object> msg = new HashMap<>();
        msg.put("id", saved.getId());
        msg.put("senderId", saved.getSenderId());
        msg.put("receiverId", saved.getReceiverId());
        msg.put("content", saved.getContent());
        msg.put("messageType", saved.getMessageType());
        msg.put("imageUrl", saved.getImageUrl());
        msg.put("isRecalled", saved.getIsRecalled());
        msg.put("quoteMessageId", saved.getQuoteMessageId());
        msg.put("quoteContent", saved.getQuoteContent());
        msg.put("quoteSenderId", saved.getQuoteSenderId());
        msg.put("createdAt", saved.getCreatedAt().toString());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(dto.getReceiverId()),
                "/queue/private.message",
                msg
        );
    }

    /**
     * WebSocket 心跳处理
     * <p>
     * 前端每10秒发送一次心跳，后端更新用户在线状态。
     * 超过30秒无心跳，用户将被标记为离线。
     * </p>
     */
    @MessageMapping("/chat.heartbeat")
    public void heartbeat(Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        onlineStatusService.heartbeat(userId);
    }
}