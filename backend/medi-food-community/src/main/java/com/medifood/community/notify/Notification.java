package com.medifood.community.notify;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("notification")
public class Notification {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private Integer type;       // 1:点赞 2:评论 3:回复 4:关注 5:收藏
    private Integer entityType; // 1:知识 2:食谱 3:动态 4:问答
    private Long entityId;
    private Long extraId;       // 如评论ID/回复ID
    private Integer isRead;     // 0 未读 1 已读
    private Date createTime;
}
