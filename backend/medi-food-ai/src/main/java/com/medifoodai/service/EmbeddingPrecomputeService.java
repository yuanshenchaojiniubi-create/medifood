package com.medifoodai.service;

import com.medifood.common.util.ElasticsearchUtil;
import com.medifoodai.service.impl.SiliconFlowEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Embedding 向量预计算服务
 * <p>
 * 每日凌晨 3 点自动预计算热门内容的 Embedding 向量，存入 Redis 预热缓存。
 * 减少首次检索时的 API 调用延迟，提升用户体验。
 * </p>
 * <p>
 * 预计算范围：
 * - 热门知识 Top 100（按热度排序）
 * - 热门食谱 Top 100（按热度排序）
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class EmbeddingPrecomputeService {

    @Autowired
    private SiliconFlowEmbeddingService embeddingService;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PRECOMPUTE_PREFIX = "ai:emb:precompute:";

    /**
     * 每日凌晨 3 点执行预计算
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void precomputeDaily() {
        log.info("========== 开始每日 Embedding 预计算 ==========");
        long startTime = System.currentTimeMillis();

        int knowledgeCount = precomputeKnowledge();
        int recipeCount = precomputeRecipes();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("========== Embedding 预计算完成：知识 {} 条，食谱 {} 条，耗时 {}ms ==========",
                knowledgeCount, recipeCount, elapsed);
    }

    /**
     * 预计算热门知识 Embedding
     */
    private int precomputeKnowledge() {
        try {
            // 从 ES 知识索引获取热门知识（按热度或最新排序）
            List<Map<String, Object>> knowledgeList = elasticsearchUtil.search(
                    "knowledge_index", "*", 0, 100, "id", "title", "content");

            int count = 0;
            for (Map<String, Object> knowledge : knowledgeList) {
                try {
                    String id = String.valueOf(knowledge.get("id"));
                    String text = buildKnowledgeText(knowledge);
                    if (text == null || text.isBlank()) continue;

                    String cacheKey = embeddingCacheKey("knowledge", id, text);
                    // 检查是否已有缓存
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) continue;

                    // 预计算并缓存
                    float[] vector = embeddingService.embed(text);
                    if (isZeroVector(vector)) {
                        log.debug("Embedding 服务不可用，跳过预计算: knowledge={}", id);
                        break; // 零向量说明服务不可用，停止预计算
                    }
                    storeVector(cacheKey, vector);
                    count++;
                } catch (Exception e) {
                    log.debug("预计算知识 Embedding 失败: id={}", knowledge.get("id"));
                }
            }
            redisTemplate.opsForValue().set(PRECOMPUTE_PREFIX + "knowledge:last", 
                    java.time.LocalDateTime.now().toString(), 25, TimeUnit.HOURS);
            return count;
        } catch (Exception e) {
            log.error("预计算知识 Embedding 异常: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 预计算热门食谱 Embedding
     */
    private int precomputeRecipes() {
        try {
            List<Map<String, Object>> recipeList = elasticsearchUtil.search(
                    "recipe_index", "*", 0, 100, "id", "title", "description", "ingredients", "effects");

            int count = 0;
            for (Map<String, Object> recipe : recipeList) {
                try {
                    String id = String.valueOf(recipe.get("id"));
                    String text = buildRecipeText(recipe);
                    if (text == null || text.isBlank()) continue;

                    String cacheKey = embeddingCacheKey("recipe", id, text);
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) continue;

                    float[] vector = embeddingService.embed(text);
                    if (isZeroVector(vector)) {
                        log.debug("Embedding 服务不可用，跳过预计算: recipe={}", id);
                        break;
                    }
                    storeVector(cacheKey, vector);
                    count++;
                } catch (Exception e) {
                    log.debug("预计算食谱 Embedding 失败: id={}", recipe.get("id"));
                }
            }
            redisTemplate.opsForValue().set(PRECOMPUTE_PREFIX + "recipe:last",
                    java.time.LocalDateTime.now().toString(), 25, TimeUnit.HOURS);
            return count;
        } catch (Exception e) {
            log.error("预计算食谱 Embedding 异常: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 获取上次预计算时间
     */
    public String getLastPrecomputeTime() {
        Object last = redisTemplate.opsForValue().get(PRECOMPUTE_PREFIX + "knowledge:last");
        return last != null ? last.toString() : "从未执行";
    }

    /**
     * 手动触发预计算
     */
    public Map<String, Object> manualTrigger() {
        long startTime = System.currentTimeMillis();
        int kCount = precomputeKnowledge();
        int rCount = precomputeRecipes();
        long elapsed = System.currentTimeMillis() - startTime;

        return Map.of(
                "knowledgeCount", kCount,
                "recipeCount", rCount,
                "elapsedMs", elapsed
        );
    }

    // ==================== 私有辅助方法 ====================

    private String buildKnowledgeText(Map<String, Object> knowledge) {
        String title = String.valueOf(knowledge.getOrDefault("title", ""));
        String content = String.valueOf(knowledge.getOrDefault("content", ""));
        return (title + " " + content).trim();
    }

    private String buildRecipeText(Map<String, Object> recipe) {
        String title = String.valueOf(recipe.getOrDefault("title", ""));
        String desc = String.valueOf(recipe.getOrDefault("description", ""));
        String ingredients = String.valueOf(recipe.getOrDefault("ingredients", ""));
        String effects = String.valueOf(recipe.getOrDefault("effects", ""));
        return (title + " " + desc + " " + ingredients + " " + effects).trim();
    }

    private String embeddingCacheKey(String type, String id, String text) {
        return "ai:emb:precompute:" + type + ":" + md5(text);
    }

    private void storeVector(String key, float[] vector) {
        // 将 float[] 转为逗号分隔字符串存储
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        redisTemplate.opsForValue().set(key, sb.toString(), 25, TimeUnit.HOURS);
    }

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

    /**
     * 判断向量是否为零向量（全零 = Embedding 服务不可用）
     */
    private boolean isZeroVector(float[] vector) {
        if (vector == null) return true;
        for (float v : vector) {
            if (v != 0.0f) return false;
        }
        return true;
    }
}