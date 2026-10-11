package com.medifood.community.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medifood.common.notification.NotificationEvent;
import com.medifood.community.mapper.NotificationAggregateMapper;
import com.medifood.community.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final long MICRO_BATCH_WINDOW_MS = 2000L;
    private static final Duration AGG_KEY_TTL = Duration.ofDays(7);
    private static final String AGG_DATA_PREFIX = "notify:agg:data:";
    private static final String AGG_ACTORS_PREFIX = "notify:agg:actors:";
    private static final String AGG_DUE_ZSET = "notify:agg:due";
    private static final String AGG_ACTIVE_ZSET = "notify:agg:active";
    private static final String AGG_UNREAD_DELTA_PREFIX = "notify:agg:unread-delta:";
    private static final String STRONG_UNREAD_PREFIX = "notify:unread:strong:";

    @Autowired
    private NotificationMapper mapper;
    @Autowired
    private NotificationAggregateMapper aggregateMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Map<Long, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    @Override
    public void register(Long userId, SseEmitter emitter) {
        EMITTERS.put(userId, emitter);
    }

    @Override
    public void unregister(Long userId) {
        EMITTERS.remove(userId);
    }

    @Override
    public void send(Notification n) {
        mapper.insert(n);
        stringRedisTemplate.opsForValue().increment(STRONG_UNREAD_PREFIX + n.getRecipientId());
        pushToUser(n.getRecipientId(), n);
    }

    @Override
    public void aggregateHighFrequency(NotificationEvent event) {
        if (event == null || event.getRecipientId() == null || event.getActorId() == null) {
            return;
        }
        String aggregateKey = buildAggregateKey(event.getRecipientId(), event.getType(), event.getEntityType(), event.getEntityId());
        String dataKey = AGG_DATA_PREFIX + aggregateKey;
        String actorsKey = AGG_ACTORS_PREFIX + aggregateKey;
        String unreadDeltaKey = AGG_UNREAD_DELTA_PREFIX + event.getRecipientId();
        long now = System.currentTimeMillis();

        stringRedisTemplate.opsForHash().put(dataKey, "recipientId", String.valueOf(event.getRecipientId()));
        stringRedisTemplate.opsForHash().put(dataKey, "type", String.valueOf(event.getType()));
        stringRedisTemplate.opsForHash().put(dataKey, "entityType", String.valueOf(event.getEntityType()));
        stringRedisTemplate.opsForHash().put(dataKey, "entityId", String.valueOf(event.getEntityId()));
        stringRedisTemplate.opsForHash().put(dataKey, "latestActorId", String.valueOf(event.getActorId()));
        stringRedisTemplate.opsForHash().put(dataKey, "lastEventTime", String.valueOf(now));
        stringRedisTemplate.opsForHash().increment(dataKey, "totalCount", 1);
        stringRedisTemplate.opsForHash().increment(dataKey, "deltaCount", 1);
        stringRedisTemplate.opsForHash().increment(dataKey, "persistDelta", 1);
        stringRedisTemplate.expire(dataKey, AGG_KEY_TTL);

        stringRedisTemplate.opsForSet().add(actorsKey, String.valueOf(event.getActorId()));
        stringRedisTemplate.expire(actorsKey, AGG_KEY_TTL);

        stringRedisTemplate.opsForHash().increment(unreadDeltaKey, aggregateKey, 1);
        stringRedisTemplate.expire(unreadDeltaKey, AGG_KEY_TTL);

        stringRedisTemplate.opsForZSet().add(AGG_DUE_ZSET, aggregateKey, now + MICRO_BATCH_WINDOW_MS);
        stringRedisTemplate.opsForZSet().add(AGG_ACTIVE_ZSET, aggregateKey, now);
    }

    @Override
    public void dispatchDueAggregates() {
        long now = System.currentTimeMillis();
        Set<String> dueKeys = stringRedisTemplate.opsForZSet().rangeByScore(AGG_DUE_ZSET, 0, now, 0, 200);
        if (dueKeys == null || dueKeys.isEmpty()) {
            return;
        }
        for (String aggregateKey : dueKeys) {
            Long removed = stringRedisTemplate.opsForZSet().remove(AGG_DUE_ZSET, aggregateKey);
            if (removed == null || removed == 0) {
                continue;
            }
            Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(AGG_DATA_PREFIX + aggregateKey);
            if (data == null || data.isEmpty()) {
                continue;
            }
            Long recipientId = parseLong(data.get("recipientId"));
            if (recipientId == null) {
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", 0L);
            payload.put("isAggregate", 1);
            payload.put("aggregateKey", aggregateKey);
            payload.put("aggregateCount", parseInt(data.get("totalCount"), 1));
            payload.put("unreadCount", parseInt(data.get("deltaCount"), 1));
            payload.put("type", parseInt(data.get("type"), 1));
            payload.put("entityType", parseInt(data.get("entityType"), 1));
            payload.put("entityId", parseLong(data.get("entityId")));
            payload.put("actorId", parseLong(data.get("latestActorId")));
            payload.put("isRead", 0);
            payload.put("createTime", new Date(parseLong(data.get("lastEventTime"), System.currentTimeMillis())));
            pushToUser(recipientId, payload);
            stringRedisTemplate.opsForHash().put(AGG_DATA_PREFIX + aggregateKey, "deltaCount", "0");
        }
    }

    @Override
    public void flushAggregatesToDb() {
        Set<String> activeKeys = stringRedisTemplate.opsForZSet().range(AGG_ACTIVE_ZSET, 0, 1000);
        if (activeKeys == null || activeKeys.isEmpty()) {
            return;
        }
        for (String aggregateKey : activeKeys) {
            AggregateParts parts = parseAggregateKey(aggregateKey);
            if (parts == null) {
                continue;
            }
            String dataKey = AGG_DATA_PREFIX + aggregateKey;
            Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(dataKey);
            if (data == null || data.isEmpty()) {
                stringRedisTemplate.opsForZSet().remove(AGG_ACTIVE_ZSET, aggregateKey);
                continue;
            }
            int deltaCount = parseInt(data.get("persistDelta"), 0);
            String unreadDeltaKey = AGG_UNREAD_DELTA_PREFIX + parts.recipientId;
            int unreadDelta = parseInt(stringRedisTemplate.opsForHash().get(unreadDeltaKey, aggregateKey), 0);
            if (deltaCount <= 0 && unreadDelta <= 0) {
                continue;
            }

            NotificationAggregate aggregate = aggregateMapper.selectOne(new LambdaQueryWrapper<NotificationAggregate>()
                    .eq(NotificationAggregate::getRecipientId, parts.recipientId)
                    .eq(NotificationAggregate::getType, parts.type)
                    .eq(NotificationAggregate::getEntityType, parts.entityType)
                    .eq(NotificationAggregate::getEntityId, parts.entityId)
                    .last("limit 1"));

            if (aggregate == null) {
                aggregate = new NotificationAggregate();
                aggregate.setRecipientId(parts.recipientId);
                aggregate.setType(parts.type);
                aggregate.setEntityType(parts.entityType);
                aggregate.setEntityId(parts.entityId);
                aggregate.setTotalCount(Math.max(deltaCount, 0));
                aggregate.setUnreadCount(Math.max(unreadDelta, 0));
                aggregate.setLatestActorId(parseLong(data.get("latestActorId")));
                aggregate.setActorSample(buildActorSample(aggregateKey));
                aggregate.setLastEventTime(new Date(parseLong(data.get("lastEventTime"), System.currentTimeMillis())));
                aggregate.setCreateTime(new Date());
                aggregate.setUpdateTime(new Date());
                aggregateMapper.insert(aggregate);
            } else {
                aggregate.setTotalCount(Math.max(0, nullSafeInt(aggregate.getTotalCount()) + deltaCount));
                aggregate.setUnreadCount(Math.max(0, nullSafeInt(aggregate.getUnreadCount()) + unreadDelta));
                aggregate.setLatestActorId(parseLong(data.get("latestActorId")));
                aggregate.setActorSample(buildActorSample(aggregateKey));
                aggregate.setLastEventTime(new Date(parseLong(data.get("lastEventTime"), System.currentTimeMillis())));
                aggregate.setUpdateTime(new Date());
                aggregateMapper.updateById(aggregate);
            }

            stringRedisTemplate.opsForHash().put(dataKey, "persistDelta", "0");
            stringRedisTemplate.opsForHash().delete(unreadDeltaKey, aggregateKey);
        }
    }

    @Override
    public Page<Map<String, Object>> list(Long userId, Integer page, Integer size) {
        Page<Notification> strongPage = new Page<>(page, size);
        LambdaQueryWrapper<Notification> strongQ = new LambdaQueryWrapper<>();
        strongQ.eq(Notification::getRecipientId, userId).orderByDesc(Notification::getCreateTime);
        Page<Notification> strong = mapper.selectPage(strongPage, strongQ);

        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(strong.getRecords().stream().map(this::toStrongMap).collect(Collectors.toList()));

        LambdaQueryWrapper<NotificationAggregate> aggQ = new LambdaQueryWrapper<>();
        aggQ.eq(NotificationAggregate::getRecipientId, userId)
                .gt(NotificationAggregate::getUnreadCount, 0)
                .orderByDesc(NotificationAggregate::getLastEventTime);
        List<NotificationAggregate> aggregates = aggregateMapper.selectList(aggQ);
        merged.addAll(aggregates.stream().map(this::toAggregateMap).collect(Collectors.toList()));

        String unreadDeltaKey = AGG_UNREAD_DELTA_PREFIX + userId;
        Map<Object, Object> pending = stringRedisTemplate.opsForHash().entries(unreadDeltaKey);
        if (pending != null && !pending.isEmpty()) {
            for (Map.Entry<Object, Object> entry : pending.entrySet()) {
                String aggregateKey = Objects.toString(entry.getKey(), "");
                int unreadDelta = parseInt(entry.getValue(), 0);
                if (aggregateKey.isEmpty() || unreadDelta <= 0) {
                    continue;
                }
                boolean exists = merged.stream().anyMatch(m -> aggregateKey.equals(m.get("aggregateKey")));
                if (exists) {
                    continue;
                }
                Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(AGG_DATA_PREFIX + aggregateKey);
                if (data == null || data.isEmpty()) {
                    continue;
                }
                Map<String, Object> m = new HashMap<>();
                m.put("id", 0L);
                m.put("isAggregate", 1);
                m.put("aggregateKey", aggregateKey);
                m.put("aggregateCount", parseInt(data.get("totalCount"), unreadDelta));
                m.put("unreadCount", unreadDelta);
                m.put("type", parseInt(data.get("type"), 1));
                m.put("entityType", parseInt(data.get("entityType"), 1));
                m.put("entityId", parseLong(data.get("entityId")));
                m.put("actorId", parseLong(data.get("latestActorId")));
                m.put("isRead", 0);
                m.put("createTime", new Date(parseLong(data.get("lastEventTime"), System.currentTimeMillis())));
                merged.add(m);
            }
        }

        merged.sort(Comparator.comparing(m -> (Date) m.getOrDefault("createTime", new Date(0L)), Comparator.reverseOrder()));
        int pageNo = page == null || page <= 0 ? 1 : page;
        int pageSize = size == null || size <= 0 ? 10 : size;
        int from = Math.max(0, (pageNo - 1) * pageSize);
        int to = Math.min(merged.size(), from + pageSize);
        List<Map<String, Object>> records = from >= to ? new ArrayList<>() : merged.subList(from, to);

        Page<Map<String, Object>> out = new Page<>(pageNo, pageSize, merged.size());
        out.setRecords(records);
        return out;
    }

    @Override
    public void markRead(Long userId, Long id) {
        if (id == null) {
            return;
        }
        if (id < 0) {
            NotificationAggregate aggregate = aggregateMapper.selectById(Math.abs(id));
            if (aggregate == null || !Objects.equals(aggregate.getRecipientId(), userId)) {
                return;
            }
            String aggregateKey = buildAggregateKey(aggregate.getRecipientId(), aggregate.getType(), aggregate.getEntityType(), aggregate.getEntityId());
            markAggregateRead(userId, aggregateKey);
            return;
        }

        Notification n = mapper.selectById(id);
        if (n != null && Objects.equals(n.getRecipientId(), userId) && !Objects.equals(n.getIsRead(), 1)) {
            n.setIsRead(1);
            mapper.updateById(n);
            decrementStrongUnread(userId);
        }
    }

    @Override
    public void markAggregateRead(Long userId, String aggregateKey) {
        if (userId == null || aggregateKey == null || aggregateKey.isBlank()) {
            return;
        }
        AggregateParts parts = parseAggregateKey(aggregateKey);
        if (parts == null || !Objects.equals(parts.recipientId, userId)) {
            return;
        }
        NotificationAggregate aggregate = aggregateMapper.selectOne(new LambdaQueryWrapper<NotificationAggregate>()
                .eq(NotificationAggregate::getRecipientId, parts.recipientId)
                .eq(NotificationAggregate::getType, parts.type)
                .eq(NotificationAggregate::getEntityType, parts.entityType)
                .eq(NotificationAggregate::getEntityId, parts.entityId)
                .last("limit 1"));
        if (aggregate != null) {
            aggregate.setUnreadCount(0);
            aggregate.setUpdateTime(new Date());
            aggregateMapper.updateById(aggregate);
        }
        String unreadDeltaKey = AGG_UNREAD_DELTA_PREFIX + userId;
        stringRedisTemplate.opsForHash().delete(unreadDeltaKey, aggregateKey);
    }

    @Override
    public void markAllRead(Long userId) {
        List<Notification> strongList = mapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0));
        for (Notification n : strongList) {
            n.setIsRead(1);
            mapper.updateById(n);
        }
        List<NotificationAggregate> aggregateList = aggregateMapper.selectList(new LambdaQueryWrapper<NotificationAggregate>()
                .eq(NotificationAggregate::getRecipientId, userId)
                .gt(NotificationAggregate::getUnreadCount, 0));
        for (NotificationAggregate n : aggregateList) {
            n.setUnreadCount(0);
            n.setUpdateTime(new Date());
            aggregateMapper.updateById(n);
        }
        stringRedisTemplate.delete(STRONG_UNREAD_PREFIX + userId);
        stringRedisTemplate.delete(AGG_UNREAD_DELTA_PREFIX + userId);
    }

    @Override
    public long unreadCount(Long userId) {
        long strongUnread = parseLong(stringRedisTemplate.opsForValue().get(STRONG_UNREAD_PREFIX + userId), 0L);
        Map<Object, Object> deltas = stringRedisTemplate.opsForHash().entries(AGG_UNREAD_DELTA_PREFIX + userId);
        long aggDelta = 0L;
        if (deltas != null) {
            for (Object v : deltas.values()) {
                aggDelta += parseLong(v, 0L);
            }
        }
        long dbStrongUnread = mapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0));
        List<NotificationAggregate> aggregates = aggregateMapper.selectList(new LambdaQueryWrapper<NotificationAggregate>()
                .eq(NotificationAggregate::getRecipientId, userId)
                .gt(NotificationAggregate::getUnreadCount, 0));
        long dbAggUnread = aggregates.stream().mapToLong(a -> nullSafeInt(a.getUnreadCount())).sum();
        return Math.max(strongUnread, dbStrongUnread) + aggDelta + dbAggUnread;
    }

    @Override
    public void delete(Long userId, Long id) {
        if (id == null) {
            return;
        }
        if (id < 0) {
            deleteAggregate(userId, String.valueOf(id));
            return;
        }
        Notification n = mapper.selectById(id);
        if (n != null && Objects.equals(n.getRecipientId(), userId)) {
            mapper.deleteById(id);
            if (!Objects.equals(n.getIsRead(), 1)) {
                decrementStrongUnread(userId);
            }
        }
    }

    @Override
    public void deleteAggregate(Long userId, String aggregateKeyOrId) {
        if (aggregateKeyOrId == null || aggregateKeyOrId.isBlank()) {
            return;
        }
        String aggregateKey = aggregateKeyOrId;
        if (!aggregateKeyOrId.contains(":")) {
            try {
                NotificationAggregate aggregate = aggregateMapper.selectById(Math.abs(Long.parseLong(aggregateKeyOrId)));
                if (aggregate == null || !Objects.equals(aggregate.getRecipientId(), userId)) {
                    return;
                }
                aggregateKey = buildAggregateKey(aggregate.getRecipientId(), aggregate.getType(), aggregate.getEntityType(), aggregate.getEntityId());
            } catch (Exception e) {
                return;
            }
        }
        AggregateParts parts = parseAggregateKey(aggregateKey);
        if (parts == null || !Objects.equals(parts.recipientId, userId)) {
            return;
        }
        NotificationAggregate aggregate = aggregateMapper.selectOne(new LambdaQueryWrapper<NotificationAggregate>()
                .eq(NotificationAggregate::getRecipientId, parts.recipientId)
                .eq(NotificationAggregate::getType, parts.type)
                .eq(NotificationAggregate::getEntityType, parts.entityType)
                .eq(NotificationAggregate::getEntityId, parts.entityId)
                .last("limit 1"));
        if (aggregate != null) {
            aggregateMapper.deleteById(aggregate.getId());
        }
        String unreadDeltaKey = AGG_UNREAD_DELTA_PREFIX + userId;
        stringRedisTemplate.opsForHash().delete(unreadDeltaKey, aggregateKey);
    }

    private void pushToUser(Long userId, Object payload) {
        SseEmitter e = EMITTERS.get(userId);
        if (e != null) {
            try {
                e.send(SseEmitter.event().name("notify").data(payload));
            } catch (Exception ex) {
                EMITTERS.remove(userId);
            }
        }
    }

    private Map<String, Object> toStrongMap(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("recipientId", n.getRecipientId());
        m.put("actorId", n.getActorId());
        m.put("type", n.getType());
        m.put("entityType", n.getEntityType());
        m.put("entityId", n.getEntityId());
        m.put("extraId", n.getExtraId());
        m.put("isRead", n.getIsRead());
        m.put("isAggregate", 0);
        m.put("createTime", n.getCreateTime());
        return m;
    }

    private Map<String, Object> toAggregateMap(NotificationAggregate n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", -Math.abs(n.getId()));
        m.put("recipientId", n.getRecipientId());
        m.put("actorId", n.getLatestActorId());
        m.put("type", n.getType());
        m.put("entityType", n.getEntityType());
        m.put("entityId", n.getEntityId());
        m.put("isRead", n.getUnreadCount() == null || n.getUnreadCount() <= 0 ? 1 : 0);
        m.put("isAggregate", 1);
        m.put("aggregateCount", n.getTotalCount());
        m.put("unreadCount", n.getUnreadCount());
        m.put("aggregateKey", buildAggregateKey(n.getRecipientId(), n.getType(), n.getEntityType(), n.getEntityId()));
        m.put("createTime", n.getLastEventTime());
        return m;
    }

    private void decrementStrongUnread(Long userId) {
        String key = STRONG_UNREAD_PREFIX + userId;
        Long left = stringRedisTemplate.opsForValue().decrement(key);
        if (left == null || left <= 0) {
            stringRedisTemplate.opsForValue().set(key, "0");
        }
    }

    private String buildAggregateKey(Long recipientId, Integer type, Integer entityType, Long entityId) {
        return recipientId + ":" + type + ":" + entityType + ":" + entityId;
    }

    private AggregateParts parseAggregateKey(String aggregateKey) {
        String[] parts = aggregateKey.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            AggregateParts p = new AggregateParts();
            p.recipientId = Long.parseLong(parts[0]);
            p.type = Integer.parseInt(parts[1]);
            p.entityType = Integer.parseInt(parts[2]);
            p.entityId = Long.parseLong(parts[3]);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildActorSample(String aggregateKey) {
        Set<String> actors = stringRedisTemplate.opsForSet().members(AGG_ACTORS_PREFIX + aggregateKey);
        if (actors == null || actors.isEmpty()) {
            return "";
        }
        return actors.stream().limit(5).collect(Collectors.joining(","));
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Long parseLong(Object value) {
        return parseLong(value, null);
    }

    private Long parseLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static class AggregateParts {
        private Long recipientId;
        private Integer type;
        private Integer entityType;
        private Long entityId;
    }

    @Override
    public String getAggregateActorSample(String aggregateKey) {
        // 先从 Redis 获取
        Set<String> actors = stringRedisTemplate.opsForSet().members(AGG_ACTORS_PREFIX + aggregateKey);
        if (actors != null && !actors.isEmpty()) {
            return String.join(",", actors);
        }
        // 从数据库获取
        AggregateParts parts = parseAggregateKey(aggregateKey);
        if (parts == null) return "";
        NotificationAggregate aggregate = aggregateMapper.selectOne(
                new LambdaQueryWrapper<NotificationAggregate>()
                        .eq(NotificationAggregate::getRecipientId, parts.recipientId)
                        .eq(NotificationAggregate::getType, parts.type)
                        .eq(NotificationAggregate::getEntityType, parts.entityType)
                        .eq(NotificationAggregate::getEntityId, parts.entityId)
                        .last("limit 1"));
        return aggregate != null && aggregate.getActorSample() != null ? aggregate.getActorSample() : "";
    }
}
