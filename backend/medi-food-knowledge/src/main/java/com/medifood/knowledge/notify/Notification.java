package com.medifood.knowledge.notify;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("notification")
public class Notification {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private Integer type;
    private Integer entityType;
    private Long entityId;
    private Long extraId;
    private Integer isRead;
    private Date createTime;
}
