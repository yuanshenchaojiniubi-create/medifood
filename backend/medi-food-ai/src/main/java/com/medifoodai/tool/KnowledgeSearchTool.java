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
 * 知识搜索工具
 * <p>
 * 基于 ES 全文检索，搜索匹配关键词的养生知识。
 * 返回标题、摘要、分类信息。
 * </p>
 *
 * @author MediFood Team
 */
@Component
public class KnowledgeSearchTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Tool(name = "knowledge_search", description = "关键词搜索养生知识，参数：keyword(String，必填)，topN(Integer，可选，默认5)。返回匹配知识的标题、摘要、分类。")
    public Map<String, Object> knowledgeSearch(String keyword, Integer topN) {
        int n = (topN == null || topN <= 0) ? 5 : Math.min(topN, 10);

        List<Map<String, Object>> results = elasticsearchUtil.search(
                "knowledge_index", keyword, 0, n,
                "id", "title", "content", "categoryId", "ingredientNames");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.get("id"));
            item.put("title", m.get("title"));
            item.put("snippet", truncate((String) m.get("content"), 200));
            item.put("categoryId", m.get("categoryId"));
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("count", items.size());
        result.put("knowledgeList", items);
        return result;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}