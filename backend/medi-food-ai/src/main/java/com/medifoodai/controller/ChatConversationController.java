package com.medifoodai.controller;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.Result;
import com.medifoodai.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 对话管理控制器
 * <p>
 * 提供对话会话的 CRUD 操作，底层使用 ConversationService（MySQL 主存储 + Redis 缓存）
 * 相比旧版纯 Redis 实现，支持服务重启后数据不丢失
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class ChatConversationController {

    @Autowired
    private ConversationService conversationService;

    /**
     * 获取用户的对话列表
     */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> listConversations(@CurrentUser Long userId) {
        try {
            List<Map<String, Object>> list = conversationService.listConversations(userId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取对话列表失败: userId={}", userId, e);
            return Result.fail("获取对话列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新对话
     */
    @PostMapping("/conversations")
    public Result<Map<String, Object>> createConversation(@CurrentUser Long userId,
                                                          @RequestBody Map<String, Object> body) {
        try {
            String title = Optional.ofNullable(body.get("title"))
                    .map(Object::toString)
                    .orElse("新会话");
            Map<String, Object> conv = conversationService.createConversation(userId, title);
            return Result.success(conv);
        } catch (Exception e) {
            log.error("创建对话失败: userId={}", userId, e);
            return Result.fail("创建对话失败: " + e.getMessage());
        }
    }

    /**
     * 重命名对话
     */
    @PatchMapping("/conversations/{id}")
    public Result<Boolean> renameConversation(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              @CurrentUser Long userId) {
        try {
            String title = Optional.ofNullable(body.get("title"))
                    .map(Object::toString)
                    .orElse(null);
            if (title == null || title.isEmpty()) {
                throw new BusinessException(400, "标题不能为空");
            }
            conversationService.renameConversation(id, userId, title);
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("重命名对话失败: id={}, userId={}", id, userId, e);
            return Result.fail("重命名失败: " + e.getMessage());
        }
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Boolean> deleteConversation(@PathVariable Long id,
                                              @CurrentUser Long userId) {
        try {
            conversationService.deleteConversation(id, userId);
            return Result.success(true);
        } catch (Exception e) {
            log.error("删除对话失败: id={}, userId={}", id, userId, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取对话的所有消息
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<Map<String, Object>>> getMessages(@PathVariable Long id,
                                                         @CurrentUser Long userId) {
        try {
            // 验证对话归属
            var conv = conversationService.getConversation(id);
            if (conv == null) {
                throw new BusinessException(404, "对话不存在");
            }
            if (!conv.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权访问此对话");
            }
            List<Map<String, Object>> messages = conversationService.getMessages(id);
            return Result.success(messages);
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取消息失败: id={}, userId={}", id, userId, e);
            return Result.fail("获取消息失败: " + e.getMessage());
        }
    }

    /**
     * 追加消息到对话
     * @return 返回新消息的数据库 ID（用于前端反馈关联和 Token 统计）
     */
    @PostMapping("/conversations/{id}/messages")
    public Result<Map<String, Object>> appendMessage(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body,
                                         @CurrentUser Long userId) {
        try {
            // 验证对话归属
            var conv = conversationService.getConversation(id);
            if (conv == null) {
                throw new BusinessException(404, "对话不存在");
            }
            if (!conv.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权操作此对话");
            }

            String role = Optional.ofNullable(body.get("role"))
                    .map(Object::toString)
                    .orElse(null);
            String content = Optional.ofNullable(body.get("content"))
                    .map(Object::toString)
                    .orElse(null);
            if (role == null || content == null) {
                throw new BusinessException(400, "role 和 content 不能为空");
            }

            // 支持可选的 tokenCount 参数
            int tokenCount = Optional.ofNullable(body.get("tokenCount"))
                    .map(obj -> obj instanceof Number ? ((Number) obj).intValue() : 0)
                    .orElse(0);

            Long messageId = conversationService.appendMessage(id, role, content, tokenCount);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", true);
            result.put("messageId", messageId);
            return Result.success(result);
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("追加消息失败: id={}, userId={}", id, userId, e);
            return Result.fail("追加消息失败: " + e.getMessage());
        }
    }
}