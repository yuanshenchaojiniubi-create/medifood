package com.medifood.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifood.message.dto.ConversationDTO;
import com.medifood.message.dto.PrivateMessageDTO;
import com.medifood.message.dto.ShareMessageDTO;
import com.medifood.message.dto.UserInfoDTO;
import com.medifood.message.entity.PrivateMessage;
import com.medifood.message.entity.UserFollow;
import com.medifood.message.feign.UserClient;
import com.medifood.message.mapper.PrivateMessageMapper;
import com.medifood.message.mapper.UserFollowMapper;
import com.medifood.message.service.OnlineStatusService;
import com.medifood.message.service.PrivateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivateMessageServiceImpl implements PrivateMessageService {

    private final PrivateMessageMapper messageMapper;
    private final UserFollowMapper followMapper;
    private final UserClient userClient;
    private final OnlineStatusService onlineStatusService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Override
    @Transactional
    public PrivateMessage send(Long senderId, PrivateMessageDTO dto) {
        if (senderId.equals(dto.getReceiverId())) {
            throw new BusinessException("不能给自己发消息哦");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }

        boolean mutual = isMutualFollow(senderId, dto.getReceiverId());

        if (!mutual) {
            long mySentCount = countSentMessages(senderId, dto.getReceiverId());
            if (mySentCount >= 1) {
                throw new BusinessException("互相关注后才能继续聊天。快去关注对方吧！");
            }
        }

        PrivateMessage msg = new PrivateMessage();
        msg.setSenderId(senderId);
        msg.setReceiverId(dto.getReceiverId());
        msg.setContent(dto.getContent());
        msg.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : "TEXT");
        msg.setImageUrl(dto.getImageUrl());
        msg.setIsRead(0);
        msg.setIsRecalled(0);
        msg.setCreatedAt(LocalDateTime.now());

        if (dto.getQuoteMessageId() != null) {
            PrivateMessage quoted = messageMapper.selectById(dto.getQuoteMessageId());
            if (quoted != null) {
                msg.setQuoteMessageId(quoted.getId());
                msg.setQuoteSenderId(quoted.getSenderId());
                String preview = quoted.getContent();
                if (preview != null && preview.length() > 50) {
                    preview = preview.substring(0, 50) + "...";
                }
                if ("VOICE".equals(quoted.getMessageType())) {
                    preview = "[语音]";
                } else if ("IMAGE".equals(quoted.getMessageType())) {
                    preview = "[图片]";
                } else if ("SHARE".equals(quoted.getMessageType())) {
                    preview = "[分享]";
                }
                msg.setQuoteContent(preview);
            }
        }

        messageMapper.insert(msg);
        return msg;
    }

    @Override
    @Transactional
    public PrivateMessage sendShare(Long senderId, ShareMessageDTO dto) {
        if (senderId.equals(dto.getReceiverId())) {
            throw new BusinessException("不能分享给自己哦");
        }
        if (!isMutualFollow(senderId, dto.getReceiverId())) {
            throw new BusinessException("只能分享给互关的好友哦");
        }
        String content;
        try {
            content = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new BusinessException("分享失败");
        }
        PrivateMessage msg = new PrivateMessage();
        msg.setSenderId(senderId);
        msg.setReceiverId(dto.getReceiverId());
        msg.setContent(content);
        msg.setMessageType("SHARE");
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg;
    }

    @Override
    public List<ConversationDTO> getConversations(Long userId) {
        Set<Long> peerIds = new LinkedHashSet<>();

        List<PrivateMessage> sent = messageMapper.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .select(PrivateMessage::getReceiverId)
                        .eq(PrivateMessage::getSenderId, userId)
                        .groupBy(PrivateMessage::getReceiverId));
        sent.forEach(m -> peerIds.add(m.getReceiverId()));

        List<PrivateMessage> received = messageMapper.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .select(PrivateMessage::getSenderId)
                        .eq(PrivateMessage::getReceiverId, userId)
                        .groupBy(PrivateMessage::getSenderId));
        received.forEach(m -> peerIds.add(m.getSenderId()));

        if (peerIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, UserInfoDTO> userInfoMap = new HashMap<>();

        return peerIds.stream().map(peerId -> {
            ConversationDTO conv = new ConversationDTO();
            conv.setUserId(peerId);

            UserInfoDTO userInfo = userInfoMap.computeIfAbsent(peerId, id -> {
                try {
                    Result<UserInfoDTO> r = userClient.getUserInfoById(id);
                    return r != null && r.getCode() == 200 ? r.getData() : null;
                } catch (Exception e) {
                    return null;
                }
            });
            if (userInfo != null) {
                conv.setNickname(userInfo.getNickname() != null ? userInfo.getNickname() : userInfo.getUsername());
                conv.setAvatar(userInfo.getAvatar());
            } else {
                conv.setNickname("用户" + peerId);
            }

            LambdaQueryWrapper<PrivateMessage> lastMsgQuery = new LambdaQueryWrapper<>();
            lastMsgQuery.and(w -> w.eq(PrivateMessage::getSenderId, userId).eq(PrivateMessage::getReceiverId, peerId)
                    .or(w2 -> w2.eq(PrivateMessage::getSenderId, peerId).eq(PrivateMessage::getReceiverId, userId)));
            lastMsgQuery.orderByDesc(PrivateMessage::getCreatedAt).last("LIMIT 1");
            PrivateMessage lastMsg = messageMapper.selectOne(lastMsgQuery);
            if (lastMsg != null) {
                String preview = lastMsg.getContent();
                if (lastMsg.getIsRecalled() != null && lastMsg.getIsRecalled() == 1) {
                    preview = "消息已撤回";
                } else if ("VOICE".equals(lastMsg.getMessageType())) {
                    preview = parseVoicePreview(lastMsg.getContent());
                } else if (lastMsg.getContent() != null && lastMsg.getContent().length() > 30) {
                    preview = lastMsg.getContent().substring(0, 30) + "...";
                }
                conv.setLastMessage(preview);
                conv.setLastTime(lastMsg.getCreatedAt().format(FMT));
            }

            Long unread = messageMapper.selectCount(
                    new LambdaQueryWrapper<PrivateMessage>()
                            .eq(PrivateMessage::getSenderId, peerId)
                            .eq(PrivateMessage::getReceiverId, userId)
                            .eq(PrivateMessage::getIsRead, 0));
            conv.setUnread(unread != null ? unread.intValue() : 0);

            conv.setIsMutualFollow(isMutualFollow(userId, peerId));
            conv.setCanSend(canSendMessage(userId, peerId));
            // 在线状态
            conv.setIsOnline(onlineStatusService.isOnline(peerId));
            // 最后一条消息是否已读（仅对我发送的消息有效）
            if (lastMsg != null && lastMsg.getSenderId().equals(userId)) {
                conv.setLastMessageRead(lastMsg.getIsRead() != null && lastMsg.getIsRead() == 1);
            }

            return conv;
        }).sorted((a, b) -> {
            String ta = a.getLastTime();
            String tb = b.getLastTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        }).collect(Collectors.toList());
    }

    @Override
    public Page<PrivateMessage> getMessagesWithUser(Long userId, Long peerUserId, Integer page, Integer size) {
        Page<PrivateMessage> p = new Page<>(page, size);
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(PrivateMessage::getSenderId, userId).eq(PrivateMessage::getReceiverId, peerUserId)
                .or(w2 -> w2.eq(PrivateMessage::getSenderId, peerUserId).eq(PrivateMessage::getReceiverId, userId)));
        wrapper.orderByAsc(PrivateMessage::getCreatedAt);
        return messageMapper.selectPage(p, wrapper);
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getReceiverId, userId)
                        .eq(PrivateMessage::getIsRead, 0));
        return count != null ? count : 0;
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long peerUserId) {
        List<PrivateMessage> unreadMessages = messageMapper.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getSenderId, peerUserId)
                        .eq(PrivateMessage::getReceiverId, userId)
                        .eq(PrivateMessage::getIsRead, 0)
        );
        if (unreadMessages.isEmpty()) {
            return;
        }
        // 批量更新已读状态
        for (PrivateMessage msg : unreadMessages) {
            msg.setIsRead(1);
            messageMapper.updateById(msg);
        }
        // 发送已读回执给对方（通过 WebSocket）
        Map<String, Object> readReceipt = new HashMap<>();
        readReceipt.put("type", "READ_RECEIPT");
        readReceipt.put("readerId", userId);
        readReceipt.put("messageIds", unreadMessages.stream().map(PrivateMessage::getId).collect(Collectors.toList()));
        readReceipt.put("count", unreadMessages.size());
        messagingTemplate.convertAndSendToUser(
                String.valueOf(peerUserId),
                "/queue/private.read-receipt",
                readReceipt
        );
        log.info("已读回执发送: reader={}, target={}, count={}", userId, peerUserId, unreadMessages.size());
    }

    @Override
    public Map<String, Object> getChatStatus(Long userId, Long peerUserId) {
        Map<String, Object> result = new HashMap<>();
        boolean mutual = isMutualFollow(userId, peerUserId);
        result.put("isMutualFollow", mutual);
        if (!mutual) {
            long mySent = countSentMessages(userId, peerUserId);
            long peerSent = countSentMessages(peerUserId, userId);
            result.put("mySentCount", mySent);
            result.put("peerSentCount", peerSent);
            result.put("canSend", mySent < 1);
        } else {
            result.put("canSend", true);
        }
        // 在线状态
        result.put("isOnline", onlineStatusService.isOnline(peerUserId));

        // 获取对方用户信息（昵称 + 头像）
        try {
            Result<UserInfoDTO> r = userClient.getUserInfoById(peerUserId);
            if (r != null && r.getCode() == 200 && r.getData() != null) {
                result.put("nickname", r.getData().getNickname() != null ? r.getData().getNickname() : r.getData().getUsername());
                result.put("avatar", r.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取对方用户信息失败: peerUserId={}", peerUserId, e);
        }

        return result;
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

    private long countSentMessages(Long senderId, Long receiverId) {
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getSenderId, senderId)
                        .eq(PrivateMessage::getReceiverId, receiverId));
        return count != null ? count : 0;
    }

    private boolean canSendMessage(Long userId, Long peerUserId) {
        if (isMutualFollow(userId, peerUserId)) {
            return true;
        }
        return countSentMessages(userId, peerUserId) < 1;
    }

    private String parseVoicePreview(String content) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(content);
            int duration = node.path("duration").asInt(0);
            String transcription = node.path("transcription").asText("");
            if (!transcription.isEmpty()) {
                return "[语音] " + transcription;
            }
            return "[语音] " + duration + "''";
        } catch (Exception e) {
            return "[语音]";
        }
    }

    @Override
    public PrivateMessage recall(Long userId, Long messageId) {
        PrivateMessage msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new BusinessException("消息不存在");
        }
        if (!msg.getSenderId().equals(userId)) {
            throw new BusinessException("只能撤回自己发送的消息");
        }
        if (msg.getIsRecalled() != null && msg.getIsRecalled() == 1) {
            throw new BusinessException("消息已撤回");
        }
        long diff = java.time.Duration.between(msg.getCreatedAt(), LocalDateTime.now()).toMinutes();
        if (diff > 2) {
            throw new BusinessException("超过2分钟的消息无法撤回");
        }
        msg.setIsRecalled(1);
        messageMapper.updateById(msg);
        return msg;
    }

    @Override
    public PrivateMessage getById(Long messageId) {
        return messageMapper.selectById(messageId);
    }
}