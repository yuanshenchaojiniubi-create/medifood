package com.medifoodai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体
 * <p>
 * 存储用户与 AI 的多轮对话会话元数据，支持摘要压缩
 * </p>
 *
 * @author MediFood Team
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    /**
     * 会话 ID（全局唯一，从 Redis 序列生成）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 会话标题（首条用户消息截取前 20 字）
     */
    private String title;

    /**
     * 对话历史摘要（3 轮之前的消息压缩，LLM 生成）
     */
    private String summary;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}