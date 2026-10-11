package com.medifood.community.audit;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.notification.NotificationEvent;
import com.medifood.common.notification.NotificationEventPublisher;
import com.medifood.common.result.Result;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.community.dto.UserInfoDTO;
import com.medifood.community.feign.KnowledgeAuditClient;
import com.medifood.community.feign.RecipeAuditClient;
import com.medifood.community.feign.UserClient;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员内容审核服务（三级漏斗第三层）
 *
 * <p>管理员对 AI 判定"疑似违规"的台账记录进行人工复核：
 * 通过（先审后发内容自动上架）/ 拒绝（删除内容）/ 拒绝并警告（删除内容并警告用户）。
 */
@Service
public class ContentAuditAdminService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /** 通知类型：管理员警告 */
    private static final int NOTIFY_TYPE_WARN = 96;
    /** 通知类型：内容审核通过 */
    private static final int NOTIFY_TYPE_AUDIT_PASS = 97;
    /** 通知类型：内容违规被删除 */
    private static final int NOTIFY_TYPE_VIOLATION = 99;

    private static final String COMMUNITY_POST_INDEX = "community_post_index";
    private static final String QA_QUESTION_INDEX = "qa_question_index";

    @Autowired
    private ContentAuditRecordMapper auditRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserClient userClient;

    @Autowired
    private RecipeAuditClient recipeAuditClient;

    @Autowired
    private KnowledgeAuditClient knowledgeAuditClient;

    @Autowired
    private NotificationEventPublisher notificationEventPublisher;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    /** 管理员审核台账 DTO（含发布者信息） */
    @Data
    public static class AuditRecordDTO {
        private Long id;
        private Integer contentType;
        private String contentTypeDesc;
        private Long contentId;
        private Long userId;
        private String username;      // 发布者用户名
        private String nickname;      // 发布者昵称
        private String title;
        private String contentSnapshot;
        private List<String> imageUrls;
        private Integer auditMode;
        private String aiCategory;
        private String aiReason;
        private Integer status;
        private Long adminId;
        private String adminRemark;
        private Date createTime;
        private Date updateTime;
    }

    /** 校验操作者为管理员（非管理员直接拒绝） */
    public void requireAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new BusinessException(403, "请先登录");
        }
        Result<Boolean> res = userClient.isAdmin(adminUserId);
        if (res == null || res.getData() == null || !Boolean.TRUE.equals(res.getData())) {
            throw new BusinessException(403, "无管理员权限");
        }
    }

    /**
     * 分页查询审核台账
     *
     * @param contentType 内容类型（null 查全部）
     * @param status      台账状态（null 查全部）
     */
    public Page<AuditRecordDTO> listRecords(Integer contentType, Integer status, Integer page, Integer size) {
        Page<ContentAuditRecord> p = new Page<>(page == null ? 1 : page, size == null ? 10 : size);
        LambdaQueryWrapper<ContentAuditRecord> wrapper = new LambdaQueryWrapper<>();
        if (contentType != null) {
            wrapper.eq(ContentAuditRecord::getContentType, contentType);
        }
        if (status != null) {
            wrapper.eq(ContentAuditRecord::getStatus, status);
        }
        wrapper.orderByDesc(ContentAuditRecord::getCreateTime);

        Page<ContentAuditRecord> result = auditRecordMapper.selectPage(p, wrapper);
        return convertToDTOPage(result);
    }

    /** 各状态数量统计（管理后台角标/看板用） */
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pending", auditRecordMapper.selectCount(
                new LambdaQueryWrapper<ContentAuditRecord>().eq(ContentAuditRecord::getStatus, 0)));
        stats.put("approved", auditRecordMapper.selectCount(
                new LambdaQueryWrapper<ContentAuditRecord>().eq(ContentAuditRecord::getStatus, 1)));
        stats.put("rejected", auditRecordMapper.selectCount(
                new LambdaQueryWrapper<ContentAuditRecord>().in(ContentAuditRecord::getStatus, 2, 3)));
        return stats;
    }

    /**
     * 管理员复核：通过
     * <p>先审后发内容（食谱/知识）：status 3→1 上架 + ES 同步 + 通知作者；
     * 先发后审内容：内容本就在线展示，仅台账结案。
     */
    public void approve(Long adminUserId, Long recordId, String remark) {
        requireAdmin(adminUserId);
        ContentAuditRecord record = getPendingRecord(recordId);

        boolean prePublish = record.getAuditMode() == 2;
        if (prePublish) {
            String table = tableName(record.getContentType());
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET status = 1 WHERE id = ? AND status = 3",
                    record.getContentId());
            if (updated > 0) {
                triggerEsResync(record.getContentType(), record.getContentId());
                sendNotification(record.getUserId(), NOTIFY_TYPE_AUDIT_PASS,
                        entityTypeOf(record.getContentType()), record.getContentId());
            }
        }

        closeRecord(record, ContentAuditRecord.STATUS_APPROVED, adminUserId, remark);
        auditLog.info("管理员审核通过: recordId={}, adminId={}, type={}, contentId={}",
                recordId, adminUserId, record.getContentType(), record.getContentId());
    }

    /**
     * 管理员复核：拒绝（删除内容），可选同时警告用户
     *
     * @param warn true 时额外向发布者发送警告通知
     */
    public void reject(Long adminUserId, Long recordId, boolean warn, String remark) {
        requireAdmin(adminUserId);
        ContentAuditRecord record = getPendingRecord(recordId);

        Integer type = record.getContentType();
        Long contentId = record.getContentId();
        String table = tableName(type);
        int deletedStatus = record.getAuditMode() == 2 ? 2 : 0;

        jdbcTemplate.update("UPDATE " + table + " SET status = ? WHERE id = ?",
                deletedStatus, contentId);

        deleteEsIndex(type, contentId);
        adjustCounters(type, contentId);

        // 通知作者：内容被删除
        sendNotification(record.getUserId(), NOTIFY_TYPE_VIOLATION,
                entityTypeOf(type), contentId);
        // 可选：发送警告通知
        if (warn) {
            sendNotification(record.getUserId(), NOTIFY_TYPE_WARN,
                    entityTypeOf(type), contentId);
        }

        closeRecord(record, warn ? ContentAuditRecord.STATUS_WARNED : ContentAuditRecord.STATUS_REJECTED,
                adminUserId, remark);
        auditLog.info("管理员审核拒绝{}: recordId={}, adminId={}, type={}, contentId={}",
                warn ? "并警告" : "", recordId, adminUserId, type, contentId);
    }

    /** 取待审记录，非待审状态直接报错（防重复处理） */
    private ContentAuditRecord getPendingRecord(Long recordId) {
        ContentAuditRecord record = auditRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (record.getStatus() != ContentAuditRecord.STATUS_PENDING) {
            throw new BusinessException("该记录已被处理，请勿重复操作");
        }
        return record;
    }

    /** 台账结案 */
    private void closeRecord(ContentAuditRecord record, int status, Long adminId, String remark) {
        record.setStatus(status);
        record.setAdminId(adminId);
        record.setAdminRemark(remark);
        record.setUpdateTime(new Date());
        auditRecordMapper.updateById(record);
    }

    /** 触发对应模块 ES 重新同步（先审后发内容上架时） */
    private void triggerEsResync(Integer type, Long contentId) {
        try {
            if (type == ContentAuditEvent.TYPE_RECIPE) {
                recipeAuditClient.resyncEs(contentId);
            } else if (type == ContentAuditEvent.TYPE_KNOWLEDGE) {
                knowledgeAuditClient.resyncEs(contentId);
            }
        } catch (Exception e) {
            auditLog.error("触发ES重新同步失败: type={}, contentId={}", type, contentId, e);
        }
    }

    private void deleteEsIndex(Integer type, Long contentId) {
        try {
            if (type == ContentAuditEvent.TYPE_POST) {
                elasticsearchUtil.deleteDocument(COMMUNITY_POST_INDEX, contentId.toString());
            } else if (type == ContentAuditEvent.TYPE_QUESTION) {
                elasticsearchUtil.deleteDocument(QA_QUESTION_INDEX, contentId.toString());
            }
        } catch (Exception e) {
            auditLog.error("ES删除失败: type={}, contentId={}", type, contentId, e);
        }
    }

    /** 评论/回答删除时扣减父内容计数 */
    private void adjustCounters(Integer type, Long contentId) {
        try {
            switch (type) {
                case ContentAuditEvent.TYPE_POST_COMMENT -> jdbcTemplate.update(
                        "UPDATE community_post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT post_id FROM post_comment WHERE id = ?)", contentId);
                case ContentAuditEvent.TYPE_RECIPE_COMMENT -> jdbcTemplate.update(
                        "UPDATE recipe SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT recipe_id FROM recipe_comment WHERE id = ?)", contentId);
                case ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> jdbcTemplate.update(
                        "UPDATE knowledge SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = " +
                                "(SELECT knowledge_id FROM knowledge_comment WHERE id = ?)", contentId);
                case ContentAuditEvent.TYPE_ANSWER -> jdbcTemplate.update(
                        "UPDATE qa_question SET answer_count = GREATEST(answer_count - 1, 0) WHERE id = " +
                                "(SELECT question_id FROM qa_answer WHERE id = ?)", contentId);
                default -> { }
            }
        } catch (Exception e) {
            auditLog.error("扣减内容计数失败: type={}, contentId={}", type, contentId, e);
        }
    }

    /** 台账实体 → DTO（补充发布者昵称、类型中文描述） */
    private Page<AuditRecordDTO> convertToDTOPage(Page<ContentAuditRecord> page) {
        List<ContentAuditRecord> records = page.getRecords();
        Map<Long, UserInfoDTO> userMap = new HashMap<>();
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream().map(ContentAuditRecord::getUserId).distinct().toList();
            for (Long uid : userIds) {
                try {
                    Result<UserInfoDTO> res = userClient.getUserInfoById(uid);
                    if (res != null && res.getData() != null) {
                        userMap.put(uid, res.getData());
                    }
                } catch (Exception ignored) { }
            }
        }

        List<AuditRecordDTO> dtoList = records.stream().map(r -> {
            AuditRecordDTO dto = new AuditRecordDTO();
            dto.setId(r.getId());
            dto.setContentType(r.getContentType());
            dto.setContentTypeDesc(describeType(r.getContentType()));
            dto.setContentId(r.getContentId());
            dto.setUserId(r.getUserId());
            UserInfoDTO u = userMap.get(r.getUserId());
            dto.setUsername(u != null ? u.getUsername() : null);
            dto.setNickname(u != null ? u.getNickname() : null);
            dto.setTitle(r.getTitle());
            dto.setContentSnapshot(r.getContentSnapshot());
            dto.setImageUrls(r.getImageUrls() == null ? null : JSONUtil.toList(r.getImageUrls(), String.class));
            dto.setAuditMode(r.getAuditMode());
            dto.setAiCategory(r.getAiCategory());
            dto.setAiReason(r.getAiReason());
            dto.setStatus(r.getStatus());
            dto.setAdminId(r.getAdminId());
            dto.setAdminRemark(r.getAdminRemark());
            dto.setCreateTime(r.getCreateTime());
            dto.setUpdateTime(r.getUpdateTime());
            return dto;
        }).collect(Collectors.toList());

        Page<AuditRecordDTO> dtoPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    private String describeType(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case ContentAuditEvent.TYPE_POST -> "社区动态";
            case ContentAuditEvent.TYPE_RECIPE -> "食谱";
            case ContentAuditEvent.TYPE_KNOWLEDGE -> "知识文章";
            case ContentAuditEvent.TYPE_POST_COMMENT -> "社区评论";
            case ContentAuditEvent.TYPE_RECIPE_COMMENT -> "食谱评论";
            case ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> "知识评论";
            case ContentAuditEvent.TYPE_QUESTION -> "提问";
            case ContentAuditEvent.TYPE_ANSWER -> "回答";
            default -> "未知类型" + type;
        };
    }

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

    private int entityTypeOf(Integer type) {
        return switch (type) {
            case ContentAuditEvent.TYPE_RECIPE, ContentAuditEvent.TYPE_RECIPE_COMMENT -> 2;
            case ContentAuditEvent.TYPE_KNOWLEDGE, ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> 1;
            case ContentAuditEvent.TYPE_QUESTION, ContentAuditEvent.TYPE_ANSWER -> 4;
            default -> 3;
        };
    }

    private void sendNotification(Long recipientId, int notifyType, int entityType, Long entityId) {
        try {
            notificationEventPublisher.publish(
                    new NotificationEvent(recipientId, 0L, notifyType, entityType, entityId, 0L));
        } catch (Exception e) {
            auditLog.error("发送审核通知失败: userId={}, notifyType={}", recipientId, notifyType, e);
        }
    }
}
