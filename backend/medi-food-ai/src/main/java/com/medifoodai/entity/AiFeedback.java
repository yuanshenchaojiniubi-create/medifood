package com.medifoodai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 回答用户反馈实体
 * <p>
 * 记录用户对 AI 回答的 👍/👎 评价，用于质量改进
 * </p>
 *
 * @author MediFood Team
 */
@Data
@TableName("ai_feedback")
public class AiFeedback {

    /**
     * 反馈 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 被评价的消息 ID（关联 ai_message.id）
     */
    private Long messageId;

    /**
     * 反馈用户 ID
     */
    private Long userId;

    /**
     * 反馈类型：positive（👍）/ negative（👎）
     */
    private String feedbackType;

    /**
     * 反馈原因（👎 时可选填）
     */
    private String reason;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}