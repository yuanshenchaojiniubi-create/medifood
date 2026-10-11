package com.medifood.community.notify;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("notification_aggregate")
public class NotificationAggregate {
    private Long id;
    private Long recipientId;
    private Integer type;
    private Integer entityType;
    private Long entityId;
    private Integer unreadCount;
    private Integer totalCount;
    private Long latestActorId;
    private String actorSample;
    private Date lastEventTime;
    private Date createTime;
    private Date updateTime;
}
