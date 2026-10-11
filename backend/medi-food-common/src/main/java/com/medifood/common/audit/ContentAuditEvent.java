package com.medifood.common.audit;

import java.io.Serializable;
import java.util.List;

/**
 * 内容审核事件（MQ 消息体）
 *
 * <p>各内容发布入口（动态/食谱/知识/评论/问答）发布内容后，
 * 通过 MQ 异步投递本事件，由 community 模块的审核监听器统一消费，
 * 调用 AI 进行二次审核，避免阻塞发布请求。
 */
public class ContentAuditEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 内容类型常量：社区动态 */
    public static final int TYPE_POST = 1;
    /** 内容类型常量：食谱（先审后发） */
    public static final int TYPE_RECIPE = 2;
    /** 内容类型常量：知识文章（先审后发） */
    public static final int TYPE_KNOWLEDGE = 3;
    /** 内容类型常量：社区评论 */
    public static final int TYPE_POST_COMMENT = 4;
    /** 内容类型常量：食谱评论 */
    public static final int TYPE_RECIPE_COMMENT = 5;
    /** 内容类型常量：知识评论 */
    public static final int TYPE_KNOWLEDGE_COMMENT = 6;
    /** 内容类型常量：问答-提问 */
    public static final int TYPE_QUESTION = 7;
    /** 内容类型常量：问答-回答 */
    public static final int TYPE_ANSWER = 8;

    /** 内容 ID（对应各内容表的主键） */
    private Long targetId;
    /** 发布者用户 ID */
    private Long userId;
    /** 内容类型：见本类 TYPE_* 常量 */
    private Integer contentType;
    /** 标题（食谱/知识/提问有标题，其余为空） */
    private String title;
    /** 正文内容 */
    private String content;
    /** 图片 URL 列表（供 AI 多模态审核） */
    private List<String> imageUrls;
    /** 消息唯一 ID（幂等去重用） */
    private String messageId;

    public ContentAuditEvent() {}

    public ContentAuditEvent(Long targetId, Long userId, Integer contentType,
                             String title, String content, List<String> imageUrls) {
        this.targetId = targetId;
        this.userId = userId;
        this.contentType = contentType;
        this.title = title;
        this.content = content;
        this.imageUrls = imageUrls;
    }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer contentType) { this.contentType = contentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    /** 该内容类型是否为"先审后发"模式（权威内容：食谱/知识文章） */
    public static boolean isPrePublishMode(int contentType) {
        return contentType == TYPE_RECIPE || contentType == TYPE_KNOWLEDGE;
    }
}
