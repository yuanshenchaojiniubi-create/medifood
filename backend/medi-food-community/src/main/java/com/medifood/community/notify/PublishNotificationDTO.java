package com.medifood.community.notify;

import lombok.Data;

@Data
public class PublishNotificationDTO {
    private Long recipientId;
    private Long actorId;
    private Integer type;        // 1:点赞 2:评论 3:回复 4:关注
    private Integer entityType;  // 1:知识 2:食谱 3:动态 4:问答
    private Long entityId;
    private Long extraId;        // 可选：评论ID/回复ID
}
