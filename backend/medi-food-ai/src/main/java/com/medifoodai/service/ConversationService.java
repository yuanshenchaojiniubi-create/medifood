package com.medifoodai.service;

import com.medifoodai.entity.AiConversation;
import com.medifoodai.entity.AiMessage;

import java.util.List;
import java.util.Map;

/**
 * 对话会话服务接口
 * <p>
 * 采用 MySQL 主存储 + Redis 缓存层的双存储策略：
 * - 写入时：先写 MySQL，再更新 Redis 缓存
 * - 读取时：先查 Redis，未命中则查 MySQL 并回填缓存
 * - 删除时：同时删除 MySQL 和 Redis
 * </p>
 *
 * @author MediFood Team
 */
public interface ConversationService {

    /**
     * 获取用户的对话列表（按更新时间倒序）
     */
    List<Map<String, Object>> listConversations(Long userId);

    /**
     * 创建新对话
     */
    Map<String, Object> createConversation(Long userId, String title);

    /**
     * 获取对话的所有消息
     */
    List<Map<String, Object>> getMessages(Long conversationId);

    /**
     * 追加消息到对话
     * @return 新消息的数据库 ID（用于后续反馈关联和 Token 统计）
     */
    Long appendMessage(Long conversationId, String role, String content);

    /**
     * 追加消息到对话（带 Token 用量）
     * @return 新消息的数据库 ID
     */
    Long appendMessage(Long conversationId, String role, String content, int tokenCount);

    /**
     * 重命名对话
     */
    void renameConversation(Long conversationId, Long userId, String title);

    /**
     * 删除对话（同时删除所有消息）
     */
    void deleteConversation(Long conversationId, Long userId);

    /**
     * 获取对话实体（内部使用）
     */
    AiConversation getConversation(Long conversationId);

    /**
     * 获取对话的消息实体列表（内部使用，不经过缓存）
     */
    List<AiMessage> getMessageEntities(Long conversationId);

    /**
     * 更新对话摘要
     */
    void updateSummary(Long conversationId, String summary);
}