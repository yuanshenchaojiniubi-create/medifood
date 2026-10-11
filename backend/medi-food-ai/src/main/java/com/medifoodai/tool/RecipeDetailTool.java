package com.medifoodai.tool;

import com.medifood.common.util.ElasticsearchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 食谱详情工具
 * <p>
 * 根据食谱 ID 从 ES recipe_index 获取完整的食谱详情。
 * 当语义搜索只返回摘要时，AI 可调用此工具获取完整内容回复用户。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class RecipeDetailTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Tool(name = "recipe_detail", description = "根据食谱ID获取完整的食谱详情，包括食材、做法步骤、功效、适合人群等。参数：recipeId(String, 必填，食谱ID)")
    public Map<String, Object> recipeDetail(String recipeId) {
        Map<String, Object> result = new HashMap<>();

        if (recipeId == null || recipeId.isEmpty()) {
            result.put("error", "食谱 ID 不能为空");
            return result;
        }

        try {
            // 尝试从 recipe_index 获取完整文档
            Map<String, Object> doc = elasticsearchUtil.getDocumentById("recipe_index", recipeId);
            if (doc == null) {
                result.put("error", "未找到 ID 为 " + recipeId + " 的食谱");
                return result;
            }

            result.put("status", "ok");
            result.put("id", recipeId);
            result.put("title", doc.getOrDefault("title", ""));
            result.put("description", doc.getOrDefault("description", ""));
            result.put("ingredients", doc.getOrDefault("ingredients", ""));
            result.put("steps", doc.getOrDefault("steps", ""));
            result.put("effects", doc.getOrDefault("effects", ""));
            result.put("suitableFor", doc.getOrDefault("suitableFor", ""));
            result.put("tagNames", doc.getOrDefault("tagNames", ""));

            log.debug("获取食谱详情成功: recipeId={}, title={}", recipeId, result.get("title"));
        } catch (Exception e) {
            log.error("获取食谱详情失败: recipeId={}, error={}", recipeId, e.getMessage());
            result.put("error", "获取食谱详情失败：" + e.getMessage());
        }

        return result;
    }
}
