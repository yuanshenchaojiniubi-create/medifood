package com.medifoodai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户行为偏好服务
 * <p>
 * 分析用户点赞/收藏的食谱和知识，提取高频标签，构建偏好摘要。
 * 数据来源：Redis 中的用户行为记录（recipe_like、recipe_collect 等 key）
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class UserBehaviorService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "ai:preference:";
    private static final long CACHE_TTL_MINUTES = 60;

    /**
     * 构建用户偏好摘要
     * <p>
     * 示例："喜好方向：调理脾胃、祛湿、春季养生"
     * </p>
     *
     * @param userId 用户 ID
     * @return 偏好摘要字符串，数据不足时返回空字符串
     */
    public String buildPreferenceSummary(Long userId) {
        if (userId == null) return "";

        // 1. 尝试从 Redis 缓存读取
        String cacheKey = CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached.toString();
        }

        try {
            // 2. 收集用户行为的标签
            Map<String, Integer> tagFrequency = new HashMap<>();

            // 从 Redis 获取用户点赞的食谱标签
            collectRecipeTags(userId, "recipe:like:" + userId, tagFrequency);
            // 从 Redis 获取用户收藏的食谱标签
            collectRecipeTags(userId, "recipe:collect:" + userId, tagFrequency);
            // 从 Redis 获取用户点赞的知识标签
            collectKnowledgeTags(userId, "knowledge:like:" + userId, tagFrequency);
            // 从 Redis 获取用户收藏的知识标签
            collectKnowledgeTags(userId, "knowledge:collect:" + userId, tagFrequency);

            if (tagFrequency.isEmpty()) {
                return "";
            }

            // 3. 取频率最高的前 5 个标签
            String summary = tagFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining("、"));

            if (!summary.isEmpty()) {
                summary = "喜好方向：" + summary;
                // 4. 存入 Redis 缓存
                redisTemplate.opsForValue().set(cacheKey, summary, CACHE_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
            }

            return summary;
        } catch (Exception e) {
            log.warn("获取用户偏好失败: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * 收集食谱相关标签
     */
    private void collectRecipeTags(Long userId, String redisKey, Map<String, Integer> frequency) {
        try {
            // 尝试从 Redis Set 获取喜欢的食谱 ID
            Set<Object> members = redisTemplate.opsForSet().members(redisKey);
            if (members != null) {
                for (Object member : members) {
                    // 尝试获取该食谱的标签
                    String recipeId = member.toString();
                    String tagKey = "recipe:tags:" + recipeId;
                    Set<Object> tags = redisTemplate.opsForSet().members(tagKey);
                    if (tags != null) {
                        for (Object tag : tags) {
                            String tagStr = tag.toString();
                            frequency.merge(tagStr, 1, Integer::sum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("收集食谱标签失败: key={}", redisKey);
        }
    }

    /**
     * 收集知识相关标签
     */
    private void collectKnowledgeTags(Long userId, String redisKey, Map<String, Integer> frequency) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(redisKey);
            if (members != null) {
                for (Object member : members) {
                    String knowledgeId = member.toString();
                    String tagKey = "knowledge:tags:" + knowledgeId;
                    Set<Object> tags = redisTemplate.opsForSet().members(tagKey);
                    if (tags != null) {
                        for (Object tag : tags) {
                            String tagStr = tag.toString();
                            frequency.merge(tagStr, 1, Integer::sum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("收集知识标签失败: key={}", redisKey);
        }
    }
}