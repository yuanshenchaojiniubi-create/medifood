package com.medifoodai.tool;

import com.medifood.common.util.ElasticsearchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食材查询工具
 * <p>
 * 查询某种食材的药性、功效、禁忌人群等详细信息。
 * 先从知识库（knowledge_index）搜索相关文章，找不到时再搜食谱库。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class IngredientLookupTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Tool(name = "ingredient_lookup", description = "查询某种食材的药性、功效、禁忌、营养成分等。参数：ingredient(String, 必填，食材名称如'枸杞''山药')")
    public Map<String, Object> ingredientLookup(String ingredient) {
        Map<String, Object> result = new HashMap<>();

        if (ingredient == null || ingredient.isEmpty()) {
            result.put("error", "食材名称不能为空");
            return result;
        }

        try {
            // 从知识库搜索该食材相关文章
            List<Map<String, Object>> knowledgeResults = elasticsearchUtil.search(
                    "knowledge_index", ingredient, 0, 3,
                    "id", "title", "content", "categoryId", "ingredientNames");

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> m : knowledgeResults) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", m.get("id"));
                item.put("title", m.get("title"));
                item.put("snippet", truncate((String) m.get("content"), 500));
                item.put("ingredients", m.get("ingredientNames"));
                items.add(item);
            }

            // 如果知识库没找到，尝试从食谱库中搜索含有该食材的食谱
            if (items.isEmpty()) {
                List<Map<String, Object>> recipeResults = elasticsearchUtil.search(
                        "recipe_index", ingredient, 0, 3,
                        "id", "title", "ingredients", "effects", "suitableFor");
                for (Map<String, Object> m : recipeResults) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", m.get("id"));
                    item.put("title", m.get("title"));
                    item.put("source", "recipe");
                    item.put("effects", m.get("effects"));
                    item.put("suitableFor", m.get("suitableFor"));
                    items.add(item);
                }
            }

            result.put("ingredient", ingredient);
            result.put("count", items.size());
            result.put("results", items);

            if (items.isEmpty()) {
                result.put("message", "未找到关于「" + ingredient + "」的详细信息，建议查阅专业资料");
            }

            log.debug("食材查询完成: ingredient={}, 匹配数={}", ingredient, items.size());
        } catch (Exception e) {
            log.error("食材查询失败: ingredient={}, error={}", ingredient, e.getMessage());
            result.put("error", "食材查询失败：" + e.getMessage());
        }

        return result;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
