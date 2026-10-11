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
 * 问答搜索工具
 * <p>
 * 专门搜索问答库（qa_index），帮用户找到类似问题的已有答案。
 * 用于补全语义搜索，当用户问"有没有人遇到过类似问题"时使用。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class QASearchTool {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Tool(name = "qa_search", description = "搜索问答库中与用户问题相似的内容，找到已有的问题和答案。参数：keyword(String, 必填，搜索关键词), topN(Integer, 可选，默认5)")
    public Map<String, Object> qaSearch(String keyword, Integer topN) {
        int n = (topN == null || topN <= 0) ? 5 : Math.min(topN, 10);
        Map<String, Object> result = new HashMap<>();

        if (keyword == null || keyword.isEmpty()) {
            result.put("error", "搜索关键词不能为空");
            return result;
        }

        try {
            List<Map<String, Object>> results = elasticsearchUtil.search(
                    "qa_index", keyword, 0, n,
                    "id", "title", "content", "categoryId");

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> m : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", m.get("id"));
                item.put("title", m.get("title"));
                item.put("snippet", truncate((String) m.get("content"), 300));
                item.put("categoryId", m.get("categoryId"));
                items.add(item);
            }

            result.put("keyword", keyword);
            result.put("count", items.size());
            result.put("qa_list", items);

            if (items.isEmpty()) {
                result.put("message", "问答库中暂无相关记录");
            }

            log.debug("问答搜索完成: keyword={}, 匹配数={}", keyword, items.size());
        } catch (Exception e) {
            log.error("问答搜索失败: keyword={}, error={}", keyword, e.getMessage());
            result.put("error", "问答搜索失败：" + e.getMessage());
        }

        return result;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
