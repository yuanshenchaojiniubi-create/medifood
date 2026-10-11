package com.medifood.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.message.dto.ConversationDTO;
import com.medifood.message.dto.PrivateMessageDTO;
import com.medifood.message.dto.ShareMessageDTO;
import com.medifood.message.entity.PrivateMessage;

import java.util.List;
import java.util.Map;

public interface PrivateMessageService {

    PrivateMessage send(Long senderId, PrivateMessageDTO dto);

    PrivateMessage sendShare(Long senderId, ShareMessageDTO dto);

    List<ConversationDTO> getConversations(Long userId);

    Page<PrivateMessage> getMessagesWithUser(Long userId, Long peerUserId, Integer page, Integer size);

    long unreadCount(Long userId);

    void markRead(Long userId, Long peerUserId);

    Map<String, Object> getChatStatus(Long userId, Long peerUserId);

    PrivateMessage recall(Long userId, Long messageId);

    PrivateMessage getById(Long messageId);
}