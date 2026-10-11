package com.medifood.community.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 内容审核台账实体
 *
 * <p>记录所有被 AI 判定为"疑似违规"的内容快照，供管理员人工复核。
 * 管理员处理结果（通过/删除/警告）也记录在本表，形成完整审核链路。
 */
@Data
@TableName("content_audit_record")
public class ContentAuditRecord {

    /** 台账记录状态：待人工审核 */
    public static final int STATUS_PENDING = 0;
    /** 台账记录状态：管理员已通过 */
    public static final int STATUS_APPROVED = 1;
    /** 台账记录状态：管理员已拒绝（内容删除） */
    public static final int STATUS_REJECTED = 2;
    /** 台账记录状态：管理员已拒绝并警告用户 */
    public static final int STATUS_WARNED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内容类型：1-动态 2-食谱 3-知识 4-社区评论 5-食谱评论 6-知识评论 7-提问 8-回答 */
    private Integer contentType;

    /** 内容 ID */
    private Long contentId;

    /** 发布者用户 ID */
    private Long userId;

    /** 标题快照（食谱/知识/提问） */
    private String title;

    /** 内容快照（管理员复核时直接查看，无需回查各业务表） */
    private String contentSnapshot;

    /** 图片 URL 列表快照（JSON 数组） */
    private String imageUrls;

    /** 审核模式：1-先发后审（方案A） 2-先审后发（方案B） */
    private Integer auditMode;

    /** AI 判定违规类别 */
    private String aiCategory;

    /** AI 判定原因 */
    private String aiReason;

    /** 状态：0-待审核 1-已通过 2-已拒绝 3-已拒绝并警告 */
    private Integer status;

    /** 审核管理员用户 ID（人工处理时填写） */
    private Long adminId;

    /** 管理员处理备注 */
    private String adminRemark;

    private Date createTime;

    private Date updateTime;
}
