package com.medifood.recipe.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.recipe.dto.RecipeDTO;
import com.medifood.recipe.entity.*;
import com.medifood.recipe.mapper.*;
import com.medifood.recipe.service.impl.RecipeServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 个性化推荐服务
 * <p>
 * 实现多层推荐策略，从个性化到通用逐级降级：
 * <ol>
 *   <li><b>内容推荐</b>：基于用户历史行为（点赞/收藏），计算标签相似度推荐同类食谱</li>
 *   <li><b>协同过滤</b>：找到相似用户，推荐他们喜欢的食谱</li>
 *   <li><b>热门兜底</b>：新用户或冷启动场景，返回全局热门食谱</li>
 * </ol>
 * 推荐结果缓存到 Redis，30分钟过期，用户行为变化时主动刷新。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class RecommendationService {

    @Autowired
    private RecipeMapper recipeMapper;

    @Autowired
    private RecipeLikeMapper likeMapper;

    @Autowired
    private RecipeCollectMapper collectMapper;

    @Autowired
    private RecipeTagRelationMapper tagRelationMapper;

    @Autowired
    private RecipeTagMapper tagMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** 推荐结果缓存 Key 前缀 */
    private static final String RECOMMEND_CACHE_KEY = "recipe:recommend:user:";

    /** 推荐缓存过期时间 */
    private static final long RECOMMEND_CACHE_TTL_MINUTES = 30;

    /** 默认推荐数量 */
    private static final int DEFAULT_RECOMMEND_SIZE = 10;

    /** 内容推荐最少需要的用户行为数 */
    private static final int MIN_USER_ACTIONS = 2;

    /**
     * 获取个性化推荐食谱
     *
     * @param userId 用户ID（null 时返回热门食谱）
     * @param size   推荐数量
     * @return 推荐食谱 ID 列表
     */
    public List<Long> recommend(Long userId, int size) {
        if (size <= 0) size = DEFAULT_RECOMMEND_SIZE;

        // 未登录用户返回热门
        if (userId == null) {
            return getHotRecipeIds(size);
        }

        // 1. 尝试从 Redis 缓存读取
        List<Long> cached = getCachedRecommendations(userId);
        if (cached != null && cached.size() >= size) {
            log.debug("推荐命中缓存: userId={}, size={}", userId, cached.size());
            return cached.subList(0, size);
        }

        // 2. 内容推荐（基于用户喜欢的食谱标签）
        List<Long> contentBased = contentBasedRecommend(userId, size);
        if (!contentBased.isEmpty()) {
            cacheRecommendations(userId, contentBased);
            return contentBased;
        }

        // 3. 协同过滤（找到相似用户）
        List<Long> collaborative = collaborativeFilter(userId, size);
        if (!collaborative.isEmpty()) {
            cacheRecommendations(userId, collaborative);
            return collaborative;
        }

        // 4. 热门兜底
        List<Long> hot = getHotRecipeIds(size);
        cacheRecommendations(userId, hot);
        return hot;
    }

    /**
     * 清除用户推荐缓存（用户行为变化时调用）
     */
    public void clearCache(Long userId) {
        if (stringRedisTemplate != null && userId != null) {
            try {
                stringRedisTemplate.delete(RECOMMEND_CACHE_KEY + userId);
                log.debug("清除用户推荐缓存: userId={}", userId);
            } catch (Exception e) {
                log.warn("清除推荐缓存失败: userId={}", userId, e);
            }
        }
    }

    // ==================== 内容推荐 ====================

    /**
     * 基于内容的推荐算法
     * <p>
     * 核心思路：
     * 1. 获取用户喜欢/收藏的食谱
     * 2. 提取这些食谱的标签集合
     * 3. 找到拥有相同标签的其他食谱
     * 4. 按标签重叠度（Jaccard 相似度）排序
     * 5. 排除用户已看过的食谱
     * </p>
     */
    private List<Long> contentBasedRecommend(Long userId, int size) {
        // 1. 获取用户历史行为（点赞 + 收藏）
        Set<Long> userLikedIds = getUserLikedRecipeIds(userId);
        Set<Long> userCollectedIds = getUserCollectedRecipeIds(userId);
        Set<Long> userHistoryIds = new HashSet<>();
        userHistoryIds.addAll(userLikedIds);
        userHistoryIds.addAll(userCollectedIds);

        if (userHistoryIds.size() < MIN_USER_ACTIONS) {
            log.debug("用户行为数据不足({}条)，跳过内容推荐: userId={}", userHistoryIds.size(), userId);
            return Collections.emptyList();
        }

        // 2. 提取用户喜欢的标签
        Set<Long> preferredTagIds = new HashSet<>();
        for (Long recipeId : userHistoryIds) {
            preferredTagIds.addAll(getRecipeTagIds(recipeId));
        }

        if (preferredTagIds.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("内容推荐: userId={}, 历史食谱={}条, 偏好标签={}个",
                userId, userHistoryIds.size(), preferredTagIds.size());

        // 3. 找到拥有相同标签的候选食谱（排除已看过的）
        Map<Long, Integer> candidateScores = new HashMap<>();
        for (Long tagId : preferredTagIds) {
            List<RecipeTagRelation> relations = tagRelationMapper.selectList(
                    new LambdaQueryWrapper<RecipeTagRelation>()
                            .eq(RecipeTagRelation::getTagId, tagId)
            );

            for (RecipeTagRelation rel : relations) {
                Long recipeId = rel.getRecipeId();
                // 排除用户已看过的
                if (userHistoryIds.contains(recipeId)) continue;
                // 累加标签重叠分
                candidateScores.merge(recipeId, 1, Integer::sum);
            }
        }

        if (candidateScores.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 按评分排序，取 Top N
        // 同时验证食谱状态为已发布
        List<Long> sortedIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 过滤掉已删除的食谱
        List<Long> validIds = new ArrayList<>();
        for (Long id : sortedIds) {
            Recipe recipe = recipeMapper.selectById(id);
            if (recipe != null && recipe.getStatus() == 1) {
                validIds.add(id);
                if (validIds.size() >= size) break;
            }
        }

        log.info("内容推荐完成: userId={}, 候选={}条, 最终={}条",
                userId, candidateScores.size(), validIds.size());
        return validIds;
    }

    // ==================== 协同过滤 ====================

    /**
     * 基于用户的协同过滤（轻量版）
     * <p>
     * 核心思路：
     * 1. 找到与当前用户喜欢相同食谱的"相似用户"
     * 2. 收集这些相似用户喜欢的其他食谱
     * 3. 按被喜欢的次数排序
     * 4. 排除当前用户已看过的
     * </p>
     */
    private List<Long> collaborativeFilter(Long userId, int size) {
        Set<Long> userLikedIds = getUserLikedRecipeIds(userId);

        if (userLikedIds.size() < MIN_USER_ACTIONS) {
            return Collections.emptyList();
        }

        // 1. 找到喜欢过相同食谱的用户
        Set<Long> similarUsers = new HashSet<>();
        for (Long recipeId : userLikedIds) {
            List<RecipeLike> likes = likeMapper.selectList(
                    new LambdaQueryWrapper<RecipeLike>()
                            .eq(RecipeLike::getRecipeId, recipeId)
            );
            for (RecipeLike like : likes) {
                if (!like.getUserId().equals(userId)) {
                    similarUsers.add(like.getUserId());
                }
            }
        }

        if (similarUsers.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("协同过滤: userId={}, 相似用户={}人", userId, similarUsers.size());

        // 2. 收集相似用户喜欢的食谱（排除当前用户已看过的）
        Map<Long, Integer> recipeScores = new HashMap<>();
        for (Long similarUserId : similarUsers) {
            List<RecipeLike> theirLikes = likeMapper.selectList(
                    new LambdaQueryWrapper<RecipeLike>()
                            .eq(RecipeLike::getUserId, similarUserId)
            );
            for (RecipeLike like : theirLikes) {
                Long recipeId = like.getRecipeId();
                if (userLikedIds.contains(recipeId)) continue;
                recipeScores.merge(recipeId, 1, Integer::sum);
            }
        }

        if (recipeScores.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 按被推荐次数排序
        List<Long> sortedIds = recipeScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 过滤有效食谱
        List<Long> validIds = new ArrayList<>();
        for (Long id : sortedIds) {
            Recipe recipe = recipeMapper.selectById(id);
            if (recipe != null && recipe.getStatus() == 1) {
                validIds.add(id);
                if (validIds.size() >= size) break;
            }
        }

        log.info("协同过滤完成: userId={}, 候选={}条, 最终={}条",
                userId, recipeScores.size(), validIds.size());
        return validIds;
    }

    // ==================== 热门兜底 ====================

    /**
     * 获取热门食谱 ID（从 Redis ZSet）
     */
    private List<Long> getHotRecipeIds(int size) {
        if (stringRedisTemplate == null) {
            return getHotRecipeIdsFromDB(size);
        }
        try {
            Set<String> topIds = stringRedisTemplate.opsForZSet()
                    .reverseRange(RecipeServiceImpl.HOT_RECIPE_ZSET_KEY, 0, size - 1);
            if (topIds != null && !topIds.isEmpty()) {
                return topIds.stream().map(Long::valueOf).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("从 Redis ZSet 获取热门食谱失败", e);
        }
        return getHotRecipeIdsFromDB(size);
    }

    private List<Long> getHotRecipeIdsFromDB(int size) {
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Recipe::getStatus, 1)
                .last("ORDER BY (like_count * 3 + view_count + comment_count * 2) DESC LIMIT " + size);
        return recipeMapper.selectList(wrapper).stream()
                .map(Recipe::getId)
                .collect(Collectors.toList());
    }

    // ==================== 缓存 ====================

    private List<Long> getCachedRecommendations(Long userId) {
        if (stringRedisTemplate == null) return null;
        try {
            String cached = stringRedisTemplate.opsForValue().get(RECOMMEND_CACHE_KEY + userId);
            if (StrUtil.isNotBlank(cached)) {
                return JSONUtil.toList(cached, Long.class);
            }
        } catch (Exception e) {
            log.warn("读取推荐缓存失败: userId={}", userId, e);
        }
        return null;
    }

    private void cacheRecommendations(Long userId, List<Long> recipeIds) {
        if (stringRedisTemplate == null || recipeIds.isEmpty()) return;
        try {
            String key = RECOMMEND_CACHE_KEY + userId;
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(recipeIds),
                    RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("缓存推荐结果: userId={}, size={}, ttl={}min",
                    userId, recipeIds.size(), RECOMMEND_CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.warn("缓存推荐结果失败: userId={}", userId, e);
        }
    }

    // ==================== 工具方法 ====================

    private Set<Long> getUserLikedRecipeIds(Long userId) {
        return likeMapper.selectList(
                new LambdaQueryWrapper<RecipeLike>()
                        .eq(RecipeLike::getUserId, userId)
        ).stream().map(RecipeLike::getRecipeId).collect(Collectors.toSet());
    }

    private Set<Long> getUserCollectedRecipeIds(Long userId) {
        return collectMapper.selectList(
                new LambdaQueryWrapper<RecipeCollect>()
                        .eq(RecipeCollect::getUserId, userId)
        ).stream().map(RecipeCollect::getRecipeId).collect(Collectors.toSet());
    }

    private Set<Long> getRecipeTagIds(Long recipeId) {
        return tagRelationMapper.selectList(
                new LambdaQueryWrapper<RecipeTagRelation>()
                        .eq(RecipeTagRelation::getRecipeId, recipeId)
        ).stream().map(RecipeTagRelation::getTagId).collect(Collectors.toSet());
    }
}