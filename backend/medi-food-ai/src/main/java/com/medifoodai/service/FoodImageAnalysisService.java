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
public class FoodImageAnalysisService {

    @Autowired
    private ApiKeyConfig apiKeyConfig;

    private String apiKey;
    private String baseUrl;
    private String model;

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private SiliconFlowEmbeddingService embeddingService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.apiKey = apiKeyConfig.getSiliconflowApiKey();
        this.baseUrl = apiKeyConfig.getSiliconflowBaseUrl();
        this.model = apiKeyConfig.getSiliconflowVisionModel();
        log.info("食物图片识别服务初始化: model={}", model);
    }

    private static final String INGREDIENT_PROMPT =
            "你是一个专业的药食同源食材识别专家。请分析图片中的食材。\n\n" +
            "请严格按照以下JSON格式返回（不要输出其他内容）：\n" +
            "{\n" +
            "  \"identified\": true或false,\n" +
            "  \"ingredients\": [\"识别的食材名称\"],\n" +
            "  \"category\": \"蔬菜/水果/肉类/海鲜/谷物/中药材/调味品/其他\",\n" +
            "  \"description\": \"食材的简要描述（50字以内）\",\n" +
            "  \"healthBenefits\": [\"药食同源角度的功效1\", \"功效2\"],\n" +
            "  \"suitableFor\": [\"适合人群\"],\n" +
            "  \"contraindications\": [\"禁忌或注意事项\"],\n" +
            "  \"searchKeywords\": [\"用于检索食谱的关键词1\", \"关键词2\"]\n" +
            "}\n\n" +
            "如果无法识别图片中的食材，返回 {\"identified\": false}";

    private static final String DISH_PROMPT =
            "你是一个专业的药食同源食谱分析专家。请分析图片中的菜品/食物。\n\n" +
            "请严格按照以下JSON格式返回（不要输出其他内容）：\n" +
            "{\n" +
            "  \"identified\": true或false,\n" +
            "  \"dishName\": \"菜品名称\",\n" +
            "  \"cuisineType\": \"菜系或类型（如粤菜/川菜/药膳/家常菜）\",\n" +
            "  \"description\": \"菜品简要描述（50字以内）\",\n" +
            "  \"mainIngredients\": [\"主要食材1\", \"主要食材2\"],\n" +
            "  \"healthBenefits\": [\"从药食同源角度分析的功效\"],\n" +
            "  \"cookingMethod\": \"简要的制作方法（100字以内）\",\n" +
            "  \"suitableFor\": [\"适合人群\"],\n" +
            "  \"searchKeywords\": [\"用于检索相似食谱的关键词\"]\n" +
            "}\n\n" +
            "如果无法识别图片中的菜品，返回 {\"identified\": false}";

    private static final String OCR_PROMPT =
            "你是一个专业的OCR文字识别助手。请仔细识别图片中的所有文字内容。\n" +
            "请严格按照以下JSON格式返回（不要输出其他内容）：\n" +
            "{\n" +
            "  \"ocrText\": \"识别出的完整文字内容\"\n" +
            "}\n" +
            "如果图片中没有文字或无法识别，返回 {\"ocrText\": \"\"}";

    public Map<String, Object> analyzeFood(String imageUrl, String mode) {
        boolean isIngredient = "ingredient".equalsIgnoreCase(mode);
        boolean isOcr = "ocr".equalsIgnoreCase(mode);
        String prompt;
        if (isOcr) {
            prompt = OCR_PROMPT;
        } else if (isIngredient) {
            prompt = INGREDIENT_PROMPT;
        } else {
            prompt = DISH_PROMPT;
        }

        log.info("========== 食物图片识别开始 ========== mode={}", mode);

        try {
            String url = baseUrl + "/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, Object>> userContent = new ArrayList<>();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            userContent.add(textPart);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> urlObj = new HashMap<>();
            urlObj.put("url", imageUrl);
            imagePart.put("image_url", urlObj);
            userContent.add(imagePart);

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("max_tokens", 800);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String reply = root.path("choices").get(0).path("message").path("content").asText();
            String cleanJson = extractJson(reply);

            log.info("视觉模型识别结果: {}", cleanJson);

            if (isOcr) {
                JsonNode resultNode = objectMapper.readTree(cleanJson);
                Map<String, Object> result = new HashMap<>();
                result.put("ocrText", resultNode.path("ocrText").asText(""));
                return result;
            }

            JsonNode resultNode = objectMapper.readTree(cleanJson);
            Map<String, Object> result = new HashMap<>();
            result.put("identified", resultNode.path("identified").asBoolean(false));

            if (resultNode.path("identified").asBoolean(false)) {
                result.put("dishName", resultNode.path("dishName").asText(""));
                result.put("ingredients", toList(resultNode.path("ingredients")));
                result.put("category", resultNode.path("category").asText(""));
                result.put("description", resultNode.path("description").asText(""));
                result.put("healthBenefits", toList(resultNode.path("healthBenefits")));
                result.put("suitableFor", toList(resultNode.path("suitableFor")));
                result.put("contraindications", toList(resultNode.path("contraindications")));
                result.put("cookingMethod", resultNode.path("cookingMethod").asText(""));
                result.put("cuisineType", resultNode.path("cuisineType").asText(""));
                result.put("mainIngredients", toList(resultNode.path("mainIngredients")));

                List<String> keywords = toList(resultNode.path("searchKeywords"));
                result.put("searchKeywords", keywords);

                if (!keywords.isEmpty()) {
                    List<Map<String, Object>> relatedRecipes = searchRelatedRecipes(keywords);
                    result.put("relatedRecipes", relatedRecipes);
                }
            }

            log.info("========== 食物图片识别结束 ==========");
            return result;

        } catch (Exception e) {
            log.error("食物图片识别异常: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("identified", false);
            error.put("error", "识别服务暂时不可用，请稍后重试");
            return error;
        }
    }

    private List<Map<String, Object>> searchRelatedRecipes(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();

        String query = String.join(" ", keywords);
        try {
            float[] vec = embeddingService.embedForQuery(query);
            var sr = elasticsearchUtil.knnSearch("recipe_semantic", vec, 3, 50,
                    "id", "title", "effects", "ingredients");
            if (sr.getRecords().isEmpty()) {
                var fallback = elasticsearchUtil.search("recipe_index", query, 0, 3,
                        "title", "ingredients", "effects");
                return fallback.stream().map(this::simplifyRecipe).collect(Collectors.toList());
            }
            return sr.getRecords().stream().map(this::simplifyRecipe).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("相关食谱检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> simplifyRecipe(Map<String, Object> recipe) {
        Map<String, Object> simple = new HashMap<>();
        simple.put("id", recipe.get("id"));
        simple.put("title", recipe.get("title"));
        simple.put("effects", recipe.get("effects"));
        simple.put("ingredients", recipe.get("ingredients"));
        return simple;
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}