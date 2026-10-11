package com.medifood.common.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsSyncEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum SyncType {
        CREATE,    // 新增
        UPDATE,    // 更新
        DELETE     // 删除
    }

    public enum EntityType {
        KNOWLEDGE,
        RECIPE,
        QA,
        COMMUNITY
    }

    private SyncType syncType;
    private EntityType entityType;
    private Long entityId;
    private String indexName;
    private String routingKey;
    private Long version;
    private Map<String, Object> payload;
    private LocalDateTime createTime;
    private int retryCount;
    private String messageId;

    public static EsSyncEvent create(Long entityId, EntityType entityType, String indexName,
                                     Long version, Map<String, Object> payload) {
        return EsSyncEvent.builder()
                .syncType(SyncType.CREATE)
                .entityType(entityType)
                .entityId(entityId)
                .indexName(indexName)
                .version(version)
                .payload(payload)
                .createTime(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    public static EsSyncEvent update(Long entityId, EntityType entityType, String indexName,
                                     Long version, Map<String, Object> payload) {
        return EsSyncEvent.builder()
                .syncType(SyncType.UPDATE)
                .entityType(entityType)
                .entityId(entityId)
                .indexName(indexName)
                .version(version)
                .payload(payload)
                .createTime(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    public static EsSyncEvent delete(Long entityId, EntityType entityType, String indexName,
                                     Long version, Map<String, Object> payload) {
        return EsSyncEvent.builder()
                .syncType(SyncType.DELETE)
                .entityType(entityType)
                .entityId(entityId)
                .indexName(indexName)
                .version(version)
                .payload(payload)
                .createTime(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
