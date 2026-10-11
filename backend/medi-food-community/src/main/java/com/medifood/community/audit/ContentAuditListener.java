package com.medifood.community.audit;

import cn.hutool.json.JSONUtil;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.audit.ContentAuditResult;
import com.medifood.common.notification.NotificationEvent;
import com.medifood.common.notification.NotificationEventPublisher;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.common.util.IdempotencyUtil;
import com.medifood.community.feign.KnowledgeAuditClient;
import com.medifood.community.feign.RecipeAuditClient;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 内容审核监听器（三级漏斗第二层的执行者）
 *
 * <p>消费所有内容模块发布的审核事件，统一执行 AI 审核并按三态结果分发：
 * <ul>
 *   <li>safe（安全）：先审后发内容（食谱/知识）自动转为已发布并同步 ES、通知作者；
 *       先发后审内容不做任何处理</li>
 *   <li>suspicious（疑似违规）：写入 content_audit_record 台账，等待管理员人工复核</li>
 *   <li>violation（明确违规）：自动删除内容（下架/软删）、清理 ES 索引、通知作者</li>
 * </ul>
 *
 * <p>说明：所有微服务共用同一 MySQL 库，此处通过 JdbcTemplate 直接更新
 * 各内容表的 status 字段（跨模块表名走白名单映射，无注入风险）。
 */
@Component
public class ContentAuditListener {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /** 通知类型：内容审核通过 */
    private static final int NOTIFY_TYPE_AUDIT_PASS = 97;
    /** 通知类型：内容违规被删除 */
    private static final int NOTIFY_TYPE_VIOLATION = 99;

    /** 社区动态 ES 索引名 */
    private static final String COMMUNITY_POST_INDEX = "community_post_index";
    /** 问答问题 ES 索引名（qa 模块发布问题时直接索引） */
    private static final String QA_QUESTION_INDEX = "qa_question_index";

    @Autowired
    private AIContentAuditService auditService;

    @Autowired
    private ContentAuditRecordMapper auditRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private NotificationEventPublisher notificationEventPublisher;

    @Autowired
    private IdempotencyUtil idempotencyUtil;

    @Autowired
    private RecipeAuditClient recipeAuditClient;

    @Autowired
    private KnowledgeAuditClient knowledgeAuditClient;

    @RabbitListener(queues = "content.audit.queue", containerFactory = "auditListenerContainerFactory")
    public void onContentAudit(ContentAuditEvent event, Message message, Channel channel) {
        auditLog.info("收到审核事件: type={}, targetId={}, userId={}, messageId={}",
                event.getContentType(), event.getTargetId(), event.getUserId(), event.getMessageId());

        if (idempotencyUtil.isConsumed(event.getMessageId())) {
            auditLog.info("消息已消费（幂等跳过）: messageId={}", event.getMessageId());
            basicAck(channel, message);
            return;
        }

        try {
            handleAudit(event);
        } catch (Exception e) {
            auditLog.error("审核处理异常: type={}, targetId={}",
                    event.getContentType(), event.getTargetId(), e);
        } finally {
            basicAck(channel, message);
        }
    }

    private void handleAudit(ContentAuditEvent event) {
        Integer type = event.getContentType();
        Long targetId = event.getTargetId();

        // 内容已被删除/处理则跳过审核（如作者自行删除）
        Integer currentStatus = queryStatus(type, targetId);
        if (currentStatus == null) {
            auditLog.info("内容不存在，跳过审核: type={}, targetId={}", type, targetId);
            return;
        }

        ContentAuditResult result = auditService.review(event);

        if (result.isSafe()) {
            handleSafe(event);
        } else if (result.isSuspicious()) {
            handleSuspicious(event, result);
        } else {
            handleViolation(event, result);
        }
    }

    /**
     * AI 判定安全：
     * 先审后发内容（食谱/知识）自动发布（status 3→1）并触发 ES 重新同步；
     * 先发后审内容无需处理。
     */
    private void handleSafe(ContentAuditEvent event) {
        if (!ContentAuditEvent.isPrePublishMode(event.getContentType())) {
            auditLog.info("审核通过: type={}, targetId={}", event.getContentType(), event.getTargetId());
            return;
        }
        String table = tableName(event.getContentType());
        int updated = jdbcTemplate.update(
                "UPDATE " + table + " SET status = 1 WHERE id = ? AND status = 3",
                event.getTargetId());
        if (updated > 0) {
            auditLog.info("先审后发内容自动发布: type={}, targetId={}", event.getContentType(), event.getTargetId());
            // 触发对应模块重新同步 ES（AI 模块消费后索引文档）
            triggerEsResync(event);
            // 通知作者内容已通过审核并发布
            sendNotification(event.getUserId(), NOTIFY_TYPE_AUDIT_PASS,
                    entityTypeOf(event.getContentType()), event.getTargetId());
        }
    }

    /**
     * AI 判定疑似违规：写入审核台账，等待管理员人工复核。
     * 先审后发内容保持"审核中"状态（对用户不可见），先发后审内容保持在线展示。
     */
    private void handleSuspicious(ContentAuditEvent event, ContentAuditResult result) {
        ContentAuditRecord record = new ContentAuditRecord();
        record.setContentType(event.getContentType());
        record.setContentId(event.getTargetId());
        record.setUserId(event.getUserId());
        record.setTitle(truncate(event.getTitle(), 500));
        record.setContentSnapshot(truncate(event.getContent(), 10000));
        record.setImageUrls(event.getImageUrls() == null ? null : JSONUtil.toJsonStr(event.getImageUrls()));
        record.setAuditMode(ContentAuditEvent.isPrePublishMode(event.getContentType()) ? 2 : 1);
        record.setAiCategory(truncate(result.getCategory(), 50));
        record.setAiReason(truncate(result.getReason(), 500));
        record.setStatus(ContentAuditRecord.STATUS_PENDING);
        record.setCreateTime(new Date());
        auditRecordMapper.insert(record);

        auditLog.info("疑似违规已记入台账等待人工复核: type={}, targetId={}, recordId={}, category={}",
                event.getContentType(), event.getTargetId(), record.getId(), result.getCategory());
    }

    /**
     * AI 判定明确违规：自动删除内容、清理 ES 索引、通知作者。
     * 先审后发内容置为"已删除"（status=2），其余内容软删（status=0）。
     */
    private void handleViolation(ContentAuditEvent event, ContentAuditResult result) {
        Integer type = event.getContentType();
        Long targetId = event.getTargetId();
        String table = tableName(type);
        int deletedStatus = ContentAuditEvent.isPrePublishMode(type) ? 2 : 0;

        int updated = jdbcTemplate.update(
                "UPDATE " + table + " SET status = ? WHERE id = ? AND status <> ?",
                deletedStatus, targetId, deletedStatus);

        if (updated == 0) {
            auditLog.info("内容已被处理，跳过删除: type={}, targetId={}", type, targetId);
            return;
        }

        auditLog.info("审核不通过，自动删除: type={}, targetId={}, category={}, reason={}",
                type, targetId, result.getCategory(), result.getReason());

        // 清理 ES 索引（仅动态/提问在发布时直接索引过）
        deleteEsIndex(type, targetId);

        // 评论/回答删除时联动扣减计数
        adjustCounters(type, targetId);

        // 通知作者内容违规被删除
        sendNotification(event.getUserId(), NOTIFY_TYPE_VIOLATION,
                entityTypeOf(type), targetId);
    }

    /** 审核通过后触发对应模块重新同步 ES（复用各模块的 ES 同步链路） */
    private void triggerEsResync(ContentAuditEvent event) {
        try {
            if (event.getContentType() == ContentAuditEvent.TYPE_RECIPE) {
                recipeAuditClient.resyncEs(event.getTargetId());
            } else if (event.getContentType() == ContentAuditEvent.TYPE_KNOWLEDGE) {
                knowledgeAuditClient.resyncEs(event.getTargetId());
            }
        } catch (Exception e) {
            auditLog.error("触发ES重新同步失败（内容已发布，不影响展示，仅影响搜索）: type={}, targetId={}",
                    event.getContentType(), event.getTargetId(), e);
        }
    }

    /** 清理 ES 索引（内容删除后从搜索结果移除） */
    private void deleteEsIndex(Integer type, Long targetId) {
        try {
            if (type == ContentAuditEvent.TYPE_POST) {
                elasticsearchUtil.deleteDocument(COMMUNITY_POST_INDEX, targetId.toString());
            } else if (type == ContentAuditEvent.TYPE_QUESTION) {
                elasticsearchUtil.deleteDocument(QA_QUESTION_INDEX, targetId.toString());
            }
        } catch (Exception e) {
            auditLog.error("ES删除失败: type={}, targetId={}", type, targetId, e);
        }
    }

    /** 评论/回答删除时扣减父内容的计数，保持数据一致 */
    private void adjustCounters(Integer type, Long targetId) {
        try {
            switch (type) {
                case ContentAuditEvent.TYPE_POST_COMMENT -> jdbcTemplate.update(
                        "UPDATE community_post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT post_id FROM post_comment WHERE id = ?)", targetId);
                case ContentAuditEvent.TYPE_RECIPE_COMMENT -> jdbcTemplate.update(
                        "UPDATE recipe SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT recipe_id FROM recipe_comment WHERE id = ?)", targetId);
                case ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> jdbcTemplate.update(
                        "UPDATE knowledge SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT knowledge_id FROM knowledge_comment WHERE id = ?)", targetId);
                case ContentAuditEvent.TYPE_ANSWER -> jdbcTemplate.update(
                        "UPDATE qa_question SET answer_count = GREATEST(answer_count - 1, 0) WHERE id = " +
                                "(SELECT question_id FROM qa_answer WHERE id = ?)", targetId);
                default -> { /* 动态/食谱/知识/提问无需联动计数 */ }
            }
        } catch (Exception e) {
            auditLog.error("扣减内容计数失败: type={}, targetId={}", type, targetId, e);
        }
    }

    /** 查询内容当前状态（内容不存在返回 null） */
    private Integer queryStatus(Integer type, Long targetId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM " + tableName(type) + " WHERE id = ?",
                    Integer.class, targetId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 内容类型 → 数据库表名（白名单映射，防 SQL 注入） */
    private String tableName(Integer type) {
        return switch (type) {
            case ContentAuditEvent.TYPE_POST -> "community_post";
            case ContentAuditEvent.TYPE_RECIPE -> "recipe";
            case ContentAuditEvent.TYPE_KNOWLEDGE -> "knowledge";
            case ContentAuditEvent.TYPE_POST_COMMENT -> "post_comment";
            case ContentAuditEvent.TYPE_RECIPE_COMMENT -> "recipe_comment";
            case ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> "knowledge_comment";
            case ContentAuditEvent.TYPE_QUESTION -> "qa_question";
            case ContentAuditEvent.TYPE_ANSWER -> "qa_answer";
            default -> throw new IllegalArgumentException("未知内容类型: " + type);
        };
    }

    /** 内容类型 → 通知实体类型（1:知识 2:食谱 3:动态 4:问答） */
    private int entityTypeOf(Integer type) {
        return switch (type) {
            case ContentAuditEvent.TYPE_RECIPE, ContentAuditEvent.TYPE_RECIPE_COMMENT -> 2;
            case ContentAuditEvent.TYPE_KNOWLEDGE, ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> 1;
            case ContentAuditEvent.TYPE_QUESTION, ContentAuditEvent.TYPE_ANSWER -> 4;
            default -> 3;
        };
    }

    /** 发送系统通知（actorId=0 表示系统消息） */
    private void sendNotification(Long recipientId, int notifyType, int entityType, Long entityId) {
        try {
            notificationEventPublisher.publish(
                    new NotificationEvent(recipientId, 0L, notifyType, entityType, entityId, 0L));
            auditLog.info("审核通知已发送: userId={}, notifyType={}, entityId={}", recipientId, notifyType, entityId);
        } catch (Exception e) {
            auditLog.error("发送审核通知失败: userId={}, notifyType={}", recipientId, notifyType, e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private void basicAck(Channel channel, Message message) {
        try {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            auditLog.error("消息确认失败", e);
        }
    }
}
