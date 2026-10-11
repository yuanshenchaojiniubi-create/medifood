package com.medifoodai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifoodai.config.ApiKeyConfig;
import com.medifoodai.service.impl.SiliconFlowEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportAnalysisService {

    @Autowired
    private ApiKeyConfig apiKeyConfig;

    private String deepseekApiKey;

    @Autowired
    private SiliconFlowEmbeddingService embeddingService;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.deepseekApiKey = apiKeyConfig.getDeepseekApiKey();
        log.info("体检报告分析服务初始化完成");
    }

    private static final String DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions";

    private static final String REPORT_ANALYSIS_PROMPT =
            "你是一个专业的药食同源养生健康顾问，同时也具备解读体检报告的能力。\n" +
            "请根据以下体检报告/健康检查结果，为用户提供专业的分析和个性化的药食同源养生建议。\n\n" +
            "请严格按以下结构回复：\n" +
            "### 一、报告摘要\n" +
            "简要总结体检报告中的关键指标和异常项。\n\n" +
            "### 二、健康评估\n" +
            "结合中医体质理论和药食同源理念，对用户的整体健康状况进行评估。\n\n" +
            "### 三、饮食调理建议\n" +
            "基于药食同源原则，推荐适合的：\n" +
            "- 日常食材（说明其功效）\n" +
            "- 推荐食谱类型\n" +
            "- 饮食注意事项\n\n" +
            "### 四、生活习惯建议\n" +
            "针对性的作息、运动、情志调节建议。\n\n" +
            "### 五、注意事项\n" +
            "需要关注的健康风险和就医提醒。\n\n" +
            "请遵守以下准则：\n" +
            "1. 回答要温暖专业，语气亲切但不过度医疗化\n" +
            "2. 回答末尾必须明确标注\"以上分析为养生参考，不能替代专业医疗诊断，身体不适应及时就医\"\n" +
            "3. 优先引用药食同源理念，推荐具体食材和食谱\n" +
            "4. 如报告关键信息不足，请诚实说明并给出通用养生建议";

    public Map<String, Object> analyzeReport(String reportText) {
        log.info("========== 体检报告分析开始 ==========");

        Map<String, Object> result = new HashMap<>();

        try {
            if (reportText == null || reportText.isBlank()) {
                result.put("success", false);
                result.put("error", "未能提取到有效的报告内容");
                return result;
            }

            result.put("reportPreview", truncate(reportText, 200));

            List<Map<String, Object>> knowledgeRefs = searchRelatedKnowledge(reportText);
            result.put("relatedKnowledge", knowledgeRefs);

            String knowledgeContext = buildKnowledgeContext(knowledgeRefs);
            String fullPrompt = REPORT_ANALYSIS_PROMPT;
            if (!knowledgeContext.isEmpty()) {
                fullPrompt = REPORT_ANALYSIS_PROMPT + "\n\n【可参考的知识库内容】：\n" + knowledgeContext
                        + "\n请在分析中引用相关知识（标注 [来源：《标题》]）。";
            }

            String analysis = callDeepSeek(fullPrompt, "请根据以下体检报告内容给出分析：\n\n" + reportText);
            result.put("success", true);
            result.put("analysis", analysis);

            log.info("========== 体检报告分析结束 ==========");
            return result;

        } catch (Exception e) {
            log.error("体检报告分析异常: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "分析服务暂时不可用: " + e.getMessage());
            return result;
        }
    }

    private List<Map<String, Object>> searchRelatedKnowledge(String reportText) {
        try {
            String queryText = reportText.length() > 500 ? reportText.substring(0, 500) : reportText;
            float[] vec = embeddingService.embedForQuery(queryText);
            var sr = elasticsearchUtil.knnSearch("knowledge_semantic", vec, 3, 50, "id", "title", "content");
            return sr.getRecords().stream()
                    .map(m -> {
                        Map<String, Object> s = new HashMap<>();
                        s.put("id", m.get("id"));
                        s.put("title", m.get("title"));
                        s.put("snippet", truncate((String) m.get("content"), 150));
                        return s;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("相关知识检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildKnowledgeContext(List<Map<String, Object>> knowledge) {
        if (knowledge.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < knowledge.size(); i++) {
            Map<String, Object> k = knowledge.get(i);
            sb.append(String.format("[知识%d]《%s》: %s\n", i + 1, k.get("title"), k.get("snippet")));
        }
        return sb.toString();
    }

    private String callDeepSeek(String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(deepseekApiKey);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "deepseek-chat");
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2000);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(DEEPSEEK_URL, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private String truncate(String str, int length) {
        if (str == null) return "";
        return str.length() > length ? str.substring(0, length) + "..." : str;
    }
}