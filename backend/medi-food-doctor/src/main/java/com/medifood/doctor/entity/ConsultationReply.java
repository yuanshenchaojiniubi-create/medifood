package com.medifood.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询回复实体
 *
 * @author MediFood Team
 */
@Data
@TableName("consultation_reply")
public class ConsultationReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 咨询ID */
    private Long consultationId;

    /** 回复用户ID */
    private Long userId;

    /** 回复内容 */
    private String content;

    /** 图片列表（JSON） */
    private String images;

    /** 是否医师回复 */
    private Integer isDoctorReply;

    /** 创建时间 */
    private LocalDateTime createTime;
}