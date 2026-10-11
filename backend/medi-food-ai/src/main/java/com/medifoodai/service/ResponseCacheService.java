package com.medifoodai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * AI 回答响应缓存服务
 * <p>
 * 对非流式 AI 回答进行缓存，相同问题二次提问时直接返回缓存结果。
 * 流式接口（ragStream）不缓存，因为每个用户有不同上下文和个性化信息。
 * </p>
 * <p>
 * 缓存策略：
 * - Redis key: ai:cache:{md5(question)}
 * - TTL: 1 小时
 * - 仅对 /ai/generate 非流式接口生效
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class ResponseCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "ai:cache:";
    private static final long CACHE_TTL_HOURS = 1;

    /**
     * 查询缓存
     *
     * @param query 用户问题
     * @return 缓存的回答，未命中返回 null
     */
    public String getCachedResponse(String query) {
        if (query == null || query.isEmpty()) return null;
        try {
            String key = CACHE_PREFIX + md5(normalize(query));
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.info("AI 回答缓存命中: query={}", truncate(query, 50));
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("查询 AI 回答缓存失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 缓存回答
     *
     * @param query    用户问题
     * @param response AI 回答
     */
    public void cacheResponse(String query, String response) {
        if (query == null || query.isEmpty() || response == null || response.isEmpty()) return;
        try {
            String key = CACHE_PREFIX + md5(normalize(query));
            redisTemplate.opsForValue().set(key, response, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("AI 回答已缓存: query={}", truncate(query, 50));
        } catch (Exception e) {
            log.warn("缓存 AI 回答失败: {}", e.getMessage());
        }
    }

    /**
     * 使缓存失效（管理员手动清除）
     *
     * @param query 用户问题
     */
    public void invalidateCache(String query) {
        if (query == null || query.isEmpty()) return;
        try {
            String key = CACHE_PREFIX + md5(normalize(query));
            redisTemplate.delete(key);
            log.info("AI 回答缓存已失效: query={}", truncate(query, 50));
        } catch (Exception e) {
            log.warn("使 AI 回答缓存失效失败: {}", e.getMessage());
        }
    }

    /**
     * 清除所有 AI 回答缓存
     */
    public void clearAllCache() {
        try {
            var keys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除所有 AI 回答缓存，共 {} 条", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除所有 AI 回答缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 标准化问题文本（移除多余空格和标点，小写化）
     */
    private String normalize(String query) {
        if (query == null) return "";
        return query.trim()
                .replaceAll("[\\s\\p{Punct}]+", "")
                .toLowerCase();
    }

    /**
     * MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}