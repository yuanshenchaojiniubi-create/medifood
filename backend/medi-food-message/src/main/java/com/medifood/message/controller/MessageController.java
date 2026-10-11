package com.medifood.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifood.common.util.OssUtil;
import com.medifood.message.dto.ConversationDTO;
import com.medifood.message.dto.PrivateMessageDTO;
import com.medifood.message.dto.ShareMessageDTO;
import com.medifood.message.entity.PrivateMessage;
import com.medifood.message.service.OnlineStatusService;
import com.medifood.message.service.PrivateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final PrivateMessageService privateMessageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;
    private final OnlineStatusService onlineStatusService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OssUtil ossUtil;

    @GetMapping("/conversations")
    public Result<List<ConversationDTO>> getConversations(@CurrentUser Long userId) {
        return Result.success(privateMessageService.getConversations(userId));
    }

    @GetMapping("/conversation/{peerUserId}")
    public Result<Page<PrivateMessage>> getMessagesWithUser(
            @CurrentUser Long userId,
            @PathVariable Long peerUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        privateMessageService.markRead(userId, peerUserId);
        return Result.success(privateMessageService.getMessagesWithUser(userId, peerUserId, page, size));
    }

    @PostMapping("/send")
    public Result<PrivateMessage> send(@CurrentUser Long userId, @RequestBody PrivateMessageDTO dto) {
        return Result.success(privateMessageService.send(userId, dto));
    }

    @PostMapping("/share")
    public Result<PrivateMessage> share(@CurrentUser Long userId, @RequestBody ShareMessageDTO dto) {
        PrivateMessage saved = privateMessageService.sendShare(userId, dto);
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
        return Result.success(saved);
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(@CurrentUser Long userId) {
        long chatUnread = privateMessageService.unreadCount(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("chatUnread", chatUnread);
        return Result.success(result);
    }

    /**
     * 统一未读计数（私聊 + 通知），供顶部铃铛使用
     * <p>
     * 用户只需看一个数字就知道是否有新消息，无需分别查看。
     * </p>
     */
    @GetMapping("/unified-unread")
    public Result<Map<String, Object>> unifiedUnreadCount(@CurrentUser Long userId) {
        long chatUnread = privateMessageService.unreadCount(userId);

        // 调用 community 模块获取通知未读数
        long notifyUnread = 0;
        try {
            ResponseEntity<Map> notifyResp = restTemplate.getForEntity(
                    "http://medi-food-community/community/notify/unread-count?uid=" + userId, Map.class);
            if (notifyResp.getBody() != null && notifyResp.getBody().get("data") != null) {
                notifyUnread = ((Number) notifyResp.getBody().get("data")).longValue();
            }
        } catch (Exception e) {
            log.warn("获取通知未读数失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("chatUnread", chatUnread);
        result.put("notifyUnread", notifyUnread);
        result.put("totalUnread", chatUnread + notifyUnread);
        return Result.success(result);
    }

    @PostMapping("/read/{peerUserId}")
    public Result<?> markRead(@CurrentUser Long userId, @PathVariable Long peerUserId) {
        privateMessageService.markRead(userId, peerUserId);
        return Result.success();
    }

    @GetMapping("/chat-status/{peerUserId}")
    public Result<Map<String, Object>> getChatStatus(
            @CurrentUser Long userId,
            @PathVariable Long peerUserId) {
        return Result.success(privateMessageService.getChatStatus(userId, peerUserId));
    }

    @PostMapping("/recall/{messageId}")
    public Result<?> recall(@CurrentUser Long userId, @PathVariable Long messageId) {
        PrivateMessage recalled = privateMessageService.recall(userId, messageId);

        Map<String, Object> recallEvent = new HashMap<>();
        recallEvent.put("type", "RECALL");
        recallEvent.put("messageId", recalled.getId());
        recallEvent.put("senderId", recalled.getSenderId());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(recalled.getReceiverId()),
                "/queue/private.recall",
                recallEvent
        );

        return Result.success();
    }

    /**
     * 查询用户在线状态
     * <p>
     * 前端在进入聊天页面时调用，显示对方是否在线。
     * </p>
     */
    @GetMapping("/online-status/{userId}")
    public Result<Map<String, Object>> getOnlineStatus(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("isOnline", onlineStatusService.isOnline(userId));
        return Result.success(result);
    }

    /**
     * 批量查询用户在线状态
     * <p>
     * 用于会话列表页面，一次查询所有会话用户的在线状态。
     * </p>
     */
    @PostMapping("/online-status/batch")
    public Result<Map<Long, Boolean>> getOnlineStatusBatch(@RequestBody List<Long> userIds) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, onlineStatusService.isOnline(userId));
        }
        return Result.success(result);
    }

    @PostMapping("/upload-voice")
    public Result<Map<String, String>> uploadVoice(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("音频文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("audio/") && !contentType.equals("video/webm"))) {
            return Result.fail("仅支持音频格式");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("dir", "voice");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://medi-food-third-party/upload",
                    requestEntity,
                    Map.class
            );

            if (response.getBody() != null && response.getBody().get("url") != null) {
                Map<String, String> result = new HashMap<>();
                result.put("url", (String) response.getBody().get("url"));
                return Result.success(result);
            }
            return fallbackUpload(file);
        } catch (Exception e) {
            log.warn("third-party上传失败，降级为OssUtil直传: {}", e.getMessage());
            return fallbackUpload(file);
        }
    }

    private Result<Map<String, String>> fallbackUpload(MultipartFile file) {
        if (ossUtil == null) {
            return Result.fail("上传服务不可用，请稍后重试");
        }
        try {
            String url = ossUtil.upload(file, "voice");
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            return Result.success(result);
        } catch (Exception e) {
            log.error("语音上传失败", e);
            return Result.fail("上传失败，请重试");
        }
    }
}