package com.medifoodai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.medifoodai.entity.AiConversation;
import com.medifoodai.entity.AiMessage;
import com.medifoodai.mapper.AiConversationMapper;
import com.medifoodai.mapper.AiMessageMapper;
import com.medifoodai.service.ConversationService;
import com.medifoodai.service.TokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话会话服务实现
 * <p>
 * 双存储策略：MySQL 主存储 + Redis 缓存层（30 分钟 TTL）
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private AiConversationMapper conversationMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private TokenUsageService tokenUsageService;

    private static final long CACHE_TTL_MINUTES = 30;

    // ==================== Redis Key 常量 ====================

    private String convListKey(Long userId) {
        return "ai:conv:" + userId + ":list";
    }

    private String convCacheKey(Long userId, Long convId) {
        return "ai:conv:" + userId + ":" + convId;
    }

    private String msgCacheKey(Long convId) {
        return "ai:msg:" + convId;
    }

    private String convSeqKey() {
        return "ai:conv:seq";
    }

    // ==================== 公开方法 ====================

    @Override
    public List<Map<String, Object>> listConversations(Long userId) {
        // 1. 尝试从 Redis 缓存读取
        String zkey = convListKey(userId);
        Set<Object> cachedIds = redisTemplate.opsForZSet().reverseRange(zkey, 0, -1);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object idObj : cachedIds) {
                String ck = convCacheKey(userId, Long.valueOf(idObj.toString()));
                Object obj = redisTemplate.opsForValue().get(ck);
                if (obj instanceof Map) {
                    list.add((Map<String, Object>) obj);
                }
            }
            if (!list.isEmpty()) {
                // 验证缓存中的对话在 MySQL 中确实存在，过滤掉脏数据
                List<Long> cachedIdList = list.stream()
                        .map(m -> Long.valueOf(m.get("id").toString()))
                        .collect(Collectors.toList());
                LambdaQueryWrapper<AiConversation> checkWrapper = new LambdaQueryWrapper<>();
                checkWrapper.in(AiConversation::getId, cachedIdList);
                List<AiConversation> existing = conversationMapper.selectList(checkWrapper);
                Set<Long> existingIds = existing.stream()
                        .map(AiConversation::getId)
                        .collect(Collectors.toSet());
                // 移除 MySQL 中不存在的脏缓存
                list.removeIf(m -> {
                    Long id = Long.valueOf(m.get("id").toString());
                    if (!existingIds.contains(id)) {
                        log.warn("清除脏缓存: userId={}, convId={}", userId, id);
                        redisTemplate.opsForZSet().remove(zkey, id.toString());
                        redisTemplate.delete(convCacheKey(userId, id));
                        return true;
                    }
                    return false;
                });
                if (!list.isEmpty()) {
                    return list;
                }
            }
        }

        // 2. Redis 未命中，从 MySQL 查询
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdatedAt);
        List<AiConversation> conversations = conversationMapper.selectList(wrapper);

        // 3. 回填 Redis 缓存
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiConversation conv : conversations) {
            Map<String, Object> convMap = toMap(conv);
            list.add(convMap);
            // 回填缓存
            redisTemplate.opsForValue().set(convCacheKey(userId, conv.getId()), convMap, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            redisTemplate.opsForZSet().add(zkey, conv.getId().toString(), toTimestamp(conv.getUpdatedAt()));
        }
        redisTemplate.expire(zkey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return list;
    }

    @Override
    @Transactional
    public Map<String, Object> createConversation(Long userId, String title) {
        String seqKey = convSeqKey();
        Long id = redisTemplate.opsForValue().increment(seqKey);
        if (id == null) {
            throw new RuntimeException("生成会话ID失败");
        }

        LocalDateTime now = LocalDateTime.now();
        AiConversation conv = new AiConversation();
        conv.setId(id);
        conv.setUserId(userId);
        conv.setTitle(title != null && !title.isEmpty() ? title : "新会话");
        conv.setCreatedAt(now);
        conv.setUpdatedAt(now);

        conversationMapper.insert(conv);

        // 更新 Redis 缓存
        Map<String, Object> convMap = toMap(conv);
        String zkey = convListKey(userId);
        redisTemplate.opsForValue().set(convCacheKey(userId, id), convMap, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForZSet().add(zkey, id.toString(), toTimestamp(now));
        redisTemplate.expire(zkey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return convMap;
    }

    @Override
    public List<Map<String, Object>> getMessages(Long conversationId) {
        // 1. 尝试从 Redis 缓存读取
        String mk = msgCacheKey(conversationId);
        List<Object> cachedList = redisTemplate.opsForList().range(mk, 0, -1);
        if (cachedList != null && !cachedList.isEmpty()) {
            return cachedList.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .collect(Collectors.toList());
        }

        // 2. Redis 未命中，从 MySQL 查询
        List<AiMessage> messages = getMessageEntities(conversationId);

        // 3. 回填 Redis 缓存
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiMessage msg : messages) {
            Map<String, Object> msgMap = toMessageMap(msg);
            list.add(msgMap);
            redisTemplate.opsForList().rightPush(mk, msgMap);
        }
        redisTemplate.expire(mk, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return list;
    }

    @Override
    @Transactional
    public Long appendMessage(Long conversationId, String role, String content) {
        return appendMessage(conversationId, role, content, 0);
    }

    @Override
    @Transactional
    public Long appendMessage(Long conversationId, String role, String content, int tokenCount) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 写入 MySQL
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(tokenCount);
        msg.setCreatedAt(now);
        messageMapper.insert(msg);

        Long messageId = msg.getId();
        log.debug("消息已保存: messageId={}, conversationId={}, role={}, tokenCount={}",
                messageId, conversationId, role, tokenCount);

        // 当保存 assistant 消息时，尝试应用流式响应中暂存的 Token 用量
        if ("assistant".equals(role) && tokenUsageService != null && tokenCount == 0) {
            tokenUsageService.applyPendingUsage(messageId, conversationId);
        }

        // 2. 更新对话的 updatedAt
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            log.warn("追加消息时找不到对话: conversationId={}", conversationId);
            return messageId;
        }
        conv.setUpdatedAt(now);
        // 首条用户消息自动设置标题
        if ("user".equals(role) && shouldAutoRename(conv.getTitle())) {
            conv.setTitle(summarizeTitle(content));
        }
        conversationMapper.updateById(conv);

        // 3. 更新 Redis 缓存
        Map<String, Object> msgMap = toMessageMap(msg);
        String mk = msgCacheKey(conversationId);
        redisTemplate.opsForList().rightPush(mk, msgMap);
        redisTemplate.expire(mk, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        // 更新会话缓存
        Map<String, Object> convMap = toMap(conv);
        String ck = convCacheKey(conv.getUserId(), conversationId);
        redisTemplate.opsForValue().set(ck, convMap, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        // 更新会话列表排序
        String zkey = convListKey(conv.getUserId());
        redisTemplate.opsForZSet().add(zkey, conversationId.toString(), toTimestamp(now));
        redisTemplate.expire(zkey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return messageId;
    }

    @Override
    @Transactional
    public void renameConversation(Long conversationId, Long userId, String title) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !Objects.equals(conv.getUserId(), userId)) {
            throw new RuntimeException("对话不存在或无权操作");
        }

        conv.setTitle(title);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        // 更新 Redis 缓存
        Map<String, Object> convMap = toMap(conv);
        redisTemplate.opsForValue().set(convCacheKey(userId, conversationId), convMap, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !Objects.equals(conv.getUserId(), userId)) {
            return; // 静默处理，幂等
        }

        // 删除 MySQL 数据
        LambdaQueryWrapper<AiMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(AiMessage::getConversationId, conversationId);
        messageMapper.delete(msgWrapper);
        conversationMapper.deleteById(conversationId);

        // 删除 Redis 缓存
        redisTemplate.delete(convCacheKey(userId, conversationId));
        redisTemplate.delete(msgCacheKey(conversationId));
        redisTemplate.opsForZSet().remove(convListKey(userId), conversationId.toString());
    }

    @Override
    public AiConversation getConversation(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    @Override
    public List<AiMessage> getMessageEntities(Long conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    @Override
    public void updateSummary(Long conversationId, String summary) {
        LambdaUpdateWrapper<AiConversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiConversation::getId, conversationId)
                .set(AiConversation::getSummary, summary);
        conversationMapper.update(null, wrapper);

        // 清除缓存（下次查询时重新加载）
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            redisTemplate.delete(convCacheKey(conv.getUserId(), conversationId));
        }
    }

    // ==================== 私有辅助方法 ====================

    private Map<String, Object> toMap(AiConversation conv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", conv.getId());
        map.put("userId", conv.getUserId());
        map.put("title", conv.getTitle());
        map.put("summary", conv.getSummary());
        map.put("createdAt", toTimestamp(conv.getCreatedAt()));
        map.put("updatedAt", toTimestamp(conv.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> toMessageMap(AiMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", msg.getId());
        map.put("conversationId", msg.getConversationId());
        map.put("role", msg.getRole());
        map.put("content", msg.getContent());
        map.put("tokenCount", msg.getTokenCount());
        map.put("createdAt", toTimestamp(msg.getCreatedAt()));
        return map;
    }

    private long toTimestamp(LocalDateTime dt) {
        if (dt == null) return 0;
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private boolean shouldAutoRename(String title) {
        return title == null || title.trim().isEmpty() || "新会话".equals(title.trim());
    }

    private String summarizeTitle(String content) {
        if (content == null) return "新会话";
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return "新会话";
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }
}