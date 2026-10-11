package com.medifood.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 膳食咨询实体
 *
 * @author MediFood Team
 */
@Data
@TableName("diet_consultation")
public class DietConsultation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 患者ID */
    private Long patientId;

    /** 医师ID */
    private Long doctorId;

    /** 咨询标题 */
    private String title;

    /** 咨询内容 */
    private String content;

    /** 图片列表（JSON） */
    private String images;

    /** 状态：PENDING-待回复 REPLIED-已回复 CLOSED-已关闭 */
    private String status;

    /** 回复数 */
    private Integer replyCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}