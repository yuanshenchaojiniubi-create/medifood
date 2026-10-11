package com.medifood.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 膳食计划实体
 *
 * @author MediFood Team
 */
@Data
@TableName("diet_plan")
public class DietPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 医师ID */
    private Long doctorId;

    /** 患者ID */
    private Long patientId;

    /** 计划标题 */
    private String title;

    /** 计划描述 */
    private String description;

    /** 详细膳食计划内容 */
    private String content;

    /** 针对症状 */
    private String targetSymptoms;

    /** 建议执行天数 */
    private Integer durationDays;

    /** 注意事项 */
    private String notes;

    /** 状态：0-草稿 1-已发布 2-已归档 */
    private Integer status;

    /** 浏览次数 */
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}