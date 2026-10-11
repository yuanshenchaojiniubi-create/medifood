package com.medifoodai.service;

import com.medifood.common.util.ElasticsearchUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAG 统一检索服务
 * 并行检索 4 个语义索引（知识、食谱、问答、社区），动态 topK，结果重排序
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class RagRetrievalService {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private EmbeddingService embeddingService;

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * 检索结果上下文
     */
    @Data
    public static class RagContext {
        private List<Hit> knowledgeHits = new ArrayList<>();
        private List<Hit> recipeHits = new ArrayList<>();
        private List<Hit> qaHits = new ArrayList<>();
        private List<Hit> communityHits = new ArrayList<>();
        /** 按 score 降序排列的跨源合并结果 */
        private List<Hit> mergedHits = new ArrayList<>();

        public boolean hasKnowledge() { return !knowledgeHits.isEmpty(); }
        public boolean hasRecipe() { return !recipeHits.isEmpty(); }
        public boolean hasQa() { return !qaHits.isEmpty(); }
        public boolean hasCommunity() { return !communityHits.isEmpty(); }
        public boolean hasAny() { return !mergedHits.isEmpty(); }
    }

    /**
     * 检索命中记录
     */
    @Data
    public static class Hit {
        private String source;     // knowledge / recipe / qa / community
        private String id;
        private String title;
        private String content;
        private String snippet;
        private double score;

        public Hit(String source, String id, String title, String content, double score) {
            this.source = source;
            this.id = id;
            this.title = title;
            this.content = content;
            this.score = score;
        }
    }

    /**
     * 执行多源并行检索
     *
     * @param queryText 用户原始问题
     * @return 统一检索上下文
     */
    public RagContext retrieve(String queryText) {
        float[] queryVec = embeddingService.embedForQuery(queryText);
        int topK = computeTopK(queryText);

        RagContext ctx = new RagContext();

        // 并行检索 4 个语义索引
        CompletableFuture<List<Hit>> knowledgeFuture = CompletableFuture.supplyAsync(() ->
                searchIndex("knowledge_semantic", "knowledge", queryVec, topK, queryText, "knowledge_index",
                        "id", "title", "content")
        ).completeOnTimeout(new ArrayList<>(), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<List<Hit>> recipeFuture = CompletableFuture.supplyAsync(() ->
                searchIndex("recipe_semantic", "recipe", queryVec, topK, queryText, "recipe_index",
                        "id", "title", "effects", "ingredients", "steps", "suitableFor")
        ).completeOnTimeout(new ArrayList<>(), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<List<Hit>> qaFuture = CompletableFuture.supplyAsync(() ->
                searchIndex("qa_semantic", "qa", queryVec, topK, queryText, "qa_index",
                        "id", "title", "content")
        ).completeOnTimeout(new ArrayList<>(), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<List<Hit>> communityFuture = CompletableFuture.supplyAsync(() ->
                searchIndex("community_semantic", "community", queryVec, topK, queryText, "community_index",
                        "id", "title", "content")
        ).completeOnTimeout(new ArrayList<>(), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            ctx.setKnowledgeHits(knowledgeFuture.get());
            ctx.setRecipeHits(recipeFuture.get());
            ctx.setQaHits(qaFuture.get());
            ctx.setCommunityHits(communityFuture.get());
        } catch (Exception e) {
            log.error("RAG 并行检索异常: {}", e.getMessage());
        }

        // 跨源重排序：按 score 降序，取前 8 条
        ctx.setMergedHits(rerank(ctx));
        return ctx;
    }

    /**
     * 动态计算 topK：短问题 → 2条，中等 → 4条，长问题 → 6条
     */
    public int computeTopK(String query) {
        if (query == null) return 4;
        int len = query.trim().length();
        if (len <= 10) return 2;
        if (len <= 30) return 4;
        return 6;
    }

    /**
     * 跨源重排序：按 ES _score 降序排列，取前 8 条
     */
    private List<Hit> rerank(RagContext ctx) {
        List<Hit> all = new ArrayList<>();
        all.addAll(ctx.getKnowledgeHits());
        all.addAll(ctx.getRecipeHits());
        all.addAll(ctx.getQaHits());
        all.addAll(ctx.getCommunityHits());
        all.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return all.stream().limit(8).collect(Collectors.toList());
    }

    /**
     * 检索单个语义索引，kNN 优先，空结果时回退到关键词搜索
     */
    private List<Hit> searchIndex(String semanticIndex, String source, float[] queryVec,
                                   int topK, String queryText, String fallbackIndex,
                                   String... fields) {
        try {
            // kNN 语义检索
            var sr = elasticsearchUtil.knnSearch(semanticIndex, queryVec, topK, 100, fields);
            if (sr.getRecords() != null && !sr.getRecords().isEmpty()) {
                return sr.getRecords().stream()
                        .map(m -> {
                            double score = m.get("_score") instanceof Number n ? n.doubleValue() : 0.0;
                            Hit hit = new Hit(source,
                                    Objects.toString(m.get("id"), ""),
                                    Objects.toString(m.get("title"), ""),
                                    Objects.toString(m.get("content"), ""),
                                    score);
                            hit.setSnippet(buildSnippet(source, m));
                            return hit;
                        })
                        .collect(Collectors.toList());
            }

            // 回退到关键词搜索
            var fallback = elasticsearchUtil.search(fallbackIndex, queryText, 0, topK, fields);
            if (fallback != null && !fallback.isEmpty()) {
                return fallback.stream()
                        .map(m -> {
                            Hit hit = new Hit(source,
                                    Objects.toString(m.get("id"), ""),
                                    Objects.toString(m.get("title"), ""),
                                    Objects.toString(m.get("content"), ""),
                                    0.0);
                            hit.setSnippet(buildSnippet(source, m));
                            return hit;
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.debug("索引 {} 检索失败（可能不存在）: {}", semanticIndex, e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 根据来源类型构建摘要
     */
    private String buildSnippet(String source, Map<String, Object> m) {
        return switch (source) {
            case "knowledge" -> truncate(Objects.toString(m.get("content"), ""), 800);
            case "recipe" -> buildRecipeSnippet(m);
            case "qa" -> truncate(Objects.toString(m.get("content"), ""), 500);
            case "community" -> truncate(Objects.toString(m.get("content"), ""), 500);
            default -> truncate(Objects.toString(m.get("content"), ""), 300);
        };
    }

    /**
     * 构建食谱摘要（包含完整字段）
     */
    private String buildRecipeSnippet(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        String effects = Objects.toString(m.get("effects"), "");
        String ingredients = Objects.toString(m.get("ingredients"), "");
        String steps = Objects.toString(m.get("steps"), "");
        String suitableFor = Objects.toString(m.get("suitableFor"), "");

        if (!effects.isEmpty()) sb.append("功效：").append(effects).append("。\n");
        if (!ingredients.isEmpty()) sb.append("食材：").append(ingredients).append("。\n");
        if (!steps.isEmpty()) sb.append("制作步骤：").append(truncate(steps, 300)).append("。\n");
        if (!suitableFor.isEmpty()) sb.append("适合人群：").append(suitableFor).append("。");

        if (sb.isEmpty()) {
            sb.append(truncate(Objects.toString(m.get("content"), ""), 500));
        }
        return sb.toString();
    }

    private String truncate(String str, int length) {
        if (str == null) return "";
        return str.length() > length ? str.substring(0, length) + "..." : str;
    }
}