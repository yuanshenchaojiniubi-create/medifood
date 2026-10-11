package com.medifoodai.tool;

import com.medifood.common.util.ElasticsearchUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食谱搜索工具
 * <p>
 * 基于 ES 全文检索，搜索匹配关键词的食谱。
 * 返回标题、功效、食材、适合人群等信息。
 * </p>
 *
 * @author MediFood Team
 */
@Component
public class RecipeSearchTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Tool(name = "recipe_search", description = "关键词搜索食谱，参数：keyword(String，必填)，topN(Integer，可选，默认5)。返回匹配食谱的标题、功效、食材、适合人群等信息。")
    public Map<String, Object> recipeSearch(String keyword, Integer topN) {
        int n = (topN == null || topN <= 0) ? 5 : Math.min(topN, 10);

        List<Map<String, Object>> results = elasticsearchUtil.search(
                "recipe_index", keyword, 0, n,
                "id", "title", "description", "ingredients", "steps", "effects", "suitableFor", "tagNames");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.get("id"));
            item.put("title", m.get("title"));
            item.put("description", m.get("description"));
            item.put("ingredients", m.get("ingredients"));
            item.put("effects", m.get("effects"));
            item.put("suitableFor", m.get("suitableFor"));
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("count", items.size());
        result.put("recipes", items);
        return result;
    }
}