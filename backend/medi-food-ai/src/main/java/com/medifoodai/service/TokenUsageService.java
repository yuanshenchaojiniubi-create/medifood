package com.medifoodai.service;

import com.medifoodai.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token 用量统计服务
 * <p>
 * 记录每次 AI 调用的 Token 消耗量，更新 ai_message 表，
 * 同时在 Redis 中维护每日/每月汇总统计。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class TokenUsageService {

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String DAILY_KEY = "ai:token:daily:";
    private static final String MONTHLY_KEY = "ai:token:monthly:";
    private static final String TOTAL_KEY = "ai:token:total";
    private static final String PENDING_KEY = "ai:token:pending:";

    /**
     * 暂存待处理的 Token 用量（流式响应完成后，等待 appendMessage 关联 messageId）
     *
     * @param conversationId   对话 ID
     * @param promptTokens     提示词 token 数
     * @param completionTokens 回答 token 数
     */
    public void stagePendingUsage(Long conversationId, int promptTokens, int completionTokens) {
        if (conversationId == null) return;
        try {
            String key = PENDING_KEY + conversationId;
            Map<String, Object> pending = new HashMap<>();
            pending.put("promptTokens", promptTokens);
            pending.put("completionTokens", completionTokens);
            pending.put("totalTokens", promptTokens + completionTokens);
            redisTemplate.opsForValue().set(key, pending, 10, TimeUnit.MINUTES);
            log.debug("Token 用量已暂存: conversationId={}, prompt={}, completion={}",
                    conversationId, promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("暂存 Token 用量失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 应用暂存的 Token 用量到具体消息
     * <p>
     * 在 appendMessage 保存 assistant 消息后调用，
     * 从 Redis 中取出暂存的 Token 数据并记录到 MySQL 和 Redis 统计。
     * </p>
     *
     * @param messageId       消息 ID
     * @param conversationId 对话 ID
     */
    public void applyPendingUsage(Long messageId, Long conversationId) {
        if (messageId == null || conversationId == null) return;
        try {
            String key = PENDING_KEY + conversationId;
            Object pending = redisTemplate.opsForValue().get(key);
            if (pending instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pendingMap = (Map<String, Object>) pending;
                int promptTokens = toInt(pendingMap.get("promptTokens"));
                int completionTokens = toInt(pendingMap.get("completionTokens"));
                recordUsage(messageId, promptTokens, completionTokens);
                redisTemplate.delete(key);
                log.debug("Token 用量已应用: messageId={}, conversationId={}, total={}",
                        messageId, conversationId, promptTokens + completionTokens);
            }
        } catch (Exception e) {
            log.warn("应用 Token 用量失败: messageId={}, conversationId={}", messageId, conversationId, e);
        }
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
    }

    /**
     * 记录 Token 用量
     *
     * @param messageId       消息 ID（可为 null）
     * @param promptTokens    提示词 token 数
     * @param completionTokens 回答 token 数
     */
    public void recordUsage(Long messageId, int promptTokens, int completionTokens) {
        if (messageId == null || messageId <= 0) return;

        int totalTokens = promptTokens + completionTokens;
        log.debug("Token 用量: messageId={}, prompt={}, completion={}, total={}",
                messageId, promptTokens, completionTokens, totalTokens);

        try {
            // 更新 MySQL 中的 token_count
            var msg = aiMessageMapper.selectById(messageId);
            if (msg != null) {
                msg.setTokenCount(totalTokens);
                aiMessageMapper.updateById(msg);
            }

            // 更新 Redis 每日统计
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String dailyKey = DAILY_KEY + today;
            redisTemplate.opsForValue().increment(dailyKey, totalTokens);
            redisTemplate.expire(dailyKey, 48, TimeUnit.HOURS);

            // 更新 Redis 每月统计
            String monthlyKey = MONTHLY_KEY + today.substring(0, 7);
            redisTemplate.opsForValue().increment(monthlyKey, totalTokens);
            redisTemplate.expire(monthlyKey, 62, TimeUnit.DAYS);

            // 更新 Redis 总计
            redisTemplate.opsForValue().increment(TOTAL_KEY, totalTokens);
        } catch (Exception e) {
            log.warn("记录 Token 用量失败: messageId={}", messageId, e);
        }
    }

    /**
     * 获取今日 Token 用量
     */
    public long getTodayUsage() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Object val = redisTemplate.opsForValue().get(DAILY_KEY + today);
        return val != null ? Long.parseLong(val.toString()) : 0L;
    }

    /**
     * 获取本月 Token 用量
     */
    public long getMonthlyUsage() {
        String monthly = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Object val = redisTemplate.opsForValue().get(MONTHLY_KEY + monthly);
        return val != null ? Long.parseLong(val.toString()) : 0L;
    }

    /**
     * 获取总 Token 用量
     */
    public long getTotalUsage() {
        Object val = redisTemplate.opsForValue().get(TOTAL_KEY);
        return val != null ? Long.parseLong(val.toString()) : 0L;
    }
}