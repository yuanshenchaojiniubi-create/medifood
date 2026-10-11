package com.medifood.recipe.sync;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifood.common.sync.EsSyncEvent;
import com.medifood.common.sync.EsSyncEventPublisher;
import com.medifood.recipe.entity.Recipe;
import com.medifood.recipe.entity.RecipeTag;
import com.medifood.recipe.entity.RecipeTagRelation;
import com.medifood.recipe.mapper.RecipeMapper;
import com.medifood.recipe.mapper.RecipeTagMapper;
import com.medifood.recipe.mapper.RecipeTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RecipeCompensateTask {

    @Autowired
    private RecipeMapper recipeMapper;

    @Autowired
    private RecipeTagRelationMapper recipeTagRelationMapper;

    @Autowired
    private RecipeTagMapper recipeTagMapper;

    @Autowired
    private EsSyncEventPublisher publisher;

    @Value("${es.sync.compensate.enabled:true}")
    private boolean compensateEnabled;

    @Value("${es.sync.compensate.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${es.sync.compensate.recipe.cron:0 0/10 * * * ?}")
    public void compensate() {
        if (!compensateEnabled) {
            log.info("食谱ES同步补偿任务已禁用");
            return;
        }
        log.info("开始食谱ES同步补偿任务，批大小: {}", batchSize);
        long startTime = System.currentTimeMillis();

        try {
            int total = compensateRecipe();
            long cost = System.currentTimeMillis() - startTime;
            log.info("食谱ES同步补偿任务完成，共 {} 条，耗时: {}ms", total, cost);
        } catch (Exception e) {
            log.error("食谱ES同步补偿任务异常: {}", e.getMessage(), e);
        }
    }

    private int compensateRecipe() {
        int total = 0;
        long maxId = 0;
        int batchNo = 0;
        while (true) {
            LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Recipe::getStatus, 1)
                    .gt(maxId > 0, Recipe::getId, maxId)
                    .orderByAsc(Recipe::getId)
                    .last("LIMIT " + batchSize);

            List<Recipe> list = recipeMapper.selectList(wrapper);
            if (list.isEmpty()) {
                log.info("食谱ES同步补偿任务扫描完成，共 {} 批，共 {} 条", batchNo, total);
                break;
            }

            batchNo++;
            log.info("食谱ES同步补偿任务开始处理第 {} 批，本批 {} 条，起始ID>{}", batchNo, list.size(), maxId);
            for (Recipe recipe : list) {
                try {
                    publisher.publishUpdate(
                            recipe.getId(),
                            EsSyncEvent.EntityType.RECIPE,
                            "recipe_index",
                            buildRecipeVersion(recipe),
                            buildRecipePayload(recipe)
                    );
                    total++;
                } catch (Exception e) {
                    log.error("补偿食谱索引失败: id={}", recipe.getId(), e);
                }
                maxId = recipe.getId();
            }
            log.info("食谱ES同步补偿任务完成第 {} 批，当前累计 {} 条，最新ID={}", batchNo, total, maxId);
        }
        return total;
    }

    private Map<String, Object> buildRecipePayload(Recipe recipe) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", recipe.getId());
        payload.put("title", recipe.getTitle());
        payload.put("description", recipe.getDescription());
        payload.put("ingredients", recipe.getIngredients());
        payload.put("steps", recipe.getSteps());
        payload.put("suitableFor", recipe.getSuitableFor());
        payload.put("effects", recipe.getEffects());
        payload.put("difficulty", recipe.getDifficulty());
        payload.put("cookingTime", recipe.getCookingTime());
        payload.put("servings", recipe.getServings());
        payload.put("userId", recipe.getUserId());
        payload.put("coverImage", recipe.getCoverImage());
        payload.put("images", parseStringList(recipe.getImages()));
        payload.put("ingredientTexts", buildStructuredRecipeTexts(recipe.getIngredients(), "name", "ingredient", "amount", "value"));
        payload.put("stepTexts", buildStructuredRecipeTexts(recipe.getSteps(), "description", "content", "step", "title"));
        payload.put("tagNames", recipeTagRelationMapper.selectList(
                        new LambdaQueryWrapper<RecipeTagRelation>()
                                .eq(RecipeTagRelation::getRecipeId, recipe.getId()))
                .stream()
                .map(RecipeTagRelation::getTagId)
                .map(recipeTagMapper::selectById)
                .filter(Objects::nonNull)
                .map(RecipeTag::getTagName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList()));
        payload.put("status", recipe.getStatus());
        payload.put("updateTime", recipe.getUpdateTime() != null ? recipe.getUpdateTime().toString() : null);
        return payload;
    }

    private Long buildRecipeVersion(Recipe recipe) {
        if (recipe.getUpdateTime() != null) {
            return recipe.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (recipe.getCreateTime() != null) {
            return recipe.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return recipe.getId();
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, String.class);
    }

    private List<String> buildStructuredRecipeTexts(String json, String... preferredKeys) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        List<String> results = new ArrayList<>();
        List<?> items = JSONUtil.toList(json, Object.class);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (item instanceof Map<?, ?> rawMap) {
                String text = buildMapSummary(rawMap, preferredKeys);
                if (StrUtil.isNotBlank(text)) {
                    results.add(text);
                }
            } else {
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    results.add(text);
                }
            }
        }
        return results;
    }

    private String buildMapSummary(Map<?, ?> rawMap, String... preferredKeys) {
        List<String> parts = new ArrayList<>();
        for (String key : preferredKeys) {
            Object value = rawMap.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                parts.add(String.valueOf(value));
            }
        }
        if (!parts.isEmpty()) {
            return String.join(" ", parts);
        }
        return rawMap.values().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }
}
