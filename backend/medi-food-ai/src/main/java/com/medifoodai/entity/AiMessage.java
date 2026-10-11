package com.medifoodai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体
 * <p>
 * 存储单条对话消息（用户提问 / AI 回复），支持 Token 用量统计
 * </p>
 *
 * @author MediFood Team
 */
@Data
@TableName("ai_message")
public class AiMessage {

    /**
     * 消息 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 ID
     */
    private Long conversationId;

    /**
     * 消息角色：user / assistant / system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * Token 消耗量（AI 回复时记录，用户消息为 0）
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}