package com.medifood.common.notification;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 跨服务投递的通知事件（与社区 notification 表语义一致）
 */
@Data
@NoArgsConstructor
public class NotificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收人用户 ID */
    private Long recipientId;
    /** 触发者用户 ID */
    private Long actorId;
    /** 1:点赞 2:评论 3:回复 4:关注 5:收藏 */
    private Integer type;
    /** 1:知识 2:食谱 3:动态 4:问答 */
    private Integer entityType;
    private Long entityId;
    /** 评论/回复父 ID 等 */
    private Long extraId;
    /** 消息唯一ID，用于幂等去重；发布者会自动生成 */
    private String messageId;

    public NotificationEvent(Long recipientId, Long actorId, Integer type,
                             Integer entityType, Long entityId, Long extraId) {
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.extraId = extraId;
    }
}
