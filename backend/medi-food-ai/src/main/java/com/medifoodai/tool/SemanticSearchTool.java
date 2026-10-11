package com.medifoodai.tool;

import com.medifood.common.util.ElasticsearchUtil;
import com.medifoodai.service.EmbeddingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class SemanticSearchTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private EmbeddingService embeddingService;

    @Tool(name = "semantic_search", description = "在知识、食谱、问答、社区语料上执行语义检索，参数：query(String)，topK(Integer，可选，默认4)。返回命中的条目摘要。")
    public Map<String, Object> semanticSearch(String query, Integer topK) {
        int k = (topK == null || topK <= 0) ? 4 : topK;
        float[] vec = embeddingService.embedForQuery(query);

        var kRes = elasticsearchUtil.knnSearch("knowledge_semantic", vec, k, 100, "id", "title", "content");
        var rRes = elasticsearchUtil.knnSearch("recipe_semantic", vec, k, 100, "id", "title", "content", "ingredients", "effects");
        var qRes = elasticsearchUtil.knnSearch("qa_semantic", vec, k, 100, "id", "title", "content");
        var cRes = elasticsearchUtil.knnSearch("community_semantic", vec, k, 100, "id", "title", "content");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : kRes.getRecords()) {
            Map<String, Object> x = new HashMap<>();
            x.put("source", "knowledge");
            x.put("id", m.get("id"));
            x.put("title", m.get("title"));
            x.put("snippet", truncate((String) m.get("content"), 300));
            items.add(x);
        }
        for (Map<String, Object> m : rRes.getRecords()) {
            Map<String, Object> x = new HashMap<>();
            x.put("source", "recipe");
            x.put("id", m.get("id"));
            x.put("title", m.get("title"));
            x.put("snippet", buildRecipeSnippet(m));
            items.add(x);
        }
        for (Map<String, Object> m : qRes.getRecords()) {
            Map<String, Object> x = new HashMap<>();
            x.put("source", "qa");
            x.put("id", m.get("id"));
            x.put("title", m.get("title"));
            x.put("snippet", truncate((String) m.get("content"), 300));
            items.add(x);
        }
        for (Map<String, Object> m : cRes.getRecords()) {
            Map<String, Object> x = new HashMap<>();
            x.put("source", "community");
            x.put("id", m.get("id"));
            x.put("title", m.get("title"));
            x.put("snippet", truncate((String) m.get("content"), 300));
            items.add(x);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("query", query);
        res.put("topK", k);
        res.put("hits", items);
        return res;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    private String buildRecipeSnippet(Map<String, Object> m) {
        String effects = Objects.toString(m.get("effects"), "");
        String ingredients = Objects.toString(m.get("ingredients"), "");
        String content = Objects.toString(m.get("content"), "");
        String base = "";
        if (!effects.isEmpty()) base += "功效：" + effects + "。";
        if (!ingredients.isEmpty()) base += "食材：" + ingredients + "。";
        if (base.isEmpty()) base = content;
        return truncate(base, 200);
    }
}

