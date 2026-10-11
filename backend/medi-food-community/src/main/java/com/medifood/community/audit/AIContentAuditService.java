package com.medifood.community.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifood.common.audit.ContentAuditEvent;
import com.medifood.common.audit.ContentAuditResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * AI 内容审核服务（三级漏斗的第二层）
 *
 * <p>调用 SiliconFlow 多模态大模型，对用户发布的内容（文字+图片）做
 * 全局合规判断，输出三态结论：
 * <ul>
 *   <li>safe —— 安全</li>
 *   <li>suspicious —— 疑似违规（擦边/不确定），转管理员人工复核</li>
 *   <li>violation —— 明确违规，自动删除</li>
 * </ul>
 *
 * <p>降级策略：AI 调用异常时一律返回 suspicious（转人工），
 * 既不放行（避免漏审）也不误删（保护正常用户），由管理员兜底。
 */
@Service
public class AIContentAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    @Value("${siliconflow.api-key}")
    private String apiKey;

    @Value("${siliconflow.audit.model}")
    private String model;

    @Value("${siliconflow.audit.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        auditLog.info("AI内容审核服务初始化: model={}", model);
    }

    private static final String AUDIT_PROMPT =
            "你是一个严格的内容安全审核专家。请审核以下用户发布的内容是否包含违规内容。\n\n" +
            "审核标准（任一命中即为违规）：\n" +
            "1. 色情低俗：性暗示、性描写、裸露图片、低俗段子\n" +
            "2. 暴力血腥：暴力场景、血腥画面、伤害他人、自残内容\n" +
            "3. 违法信息：毒品、赌博、诈骗、传销等违法内容\n" +
            "4. 人身攻击：辱骂、诽谤、仇恨言论、地域歧视\n" +
            "5. 广告营销：明显商业广告、二维码引流、联系方式营销\n" +
            "6. 虚假医疗：夸大疗效、伪科学养生偏方（如包治百病）\n\n" +
            "判定规则（三选一）：\n" +
            "- safe：内容明确安全合规\n" +
            "- violation：内容明确命中上述任一违规标准\n" +
            "- suspicious：内容疑似违规但不确定（擦边、隐晦表达、语境模糊、无法确定意图）\n\n" +
            "请严格按照以下JSON格式返回（不要返回其他内容）：\n" +
            "{\"verdict\": \"safe或suspicious或violation\", \"reason\": \"判定原因简述\", \"category\": \"违规类别（色情低俗/暴力血腥/违法信息/人身攻击/广告营销/虚假医疗/正常/疑似）\"}\n\n" +
            "如果内容安全，返回：{\"verdict\": \"safe\", \"reason\": \"内容合规\", \"category\": \"正常\"}";

    /**
     * 审核单条内容（文字 + 可选图片）
     *
     * @param event 审核事件（含内容类型、标题、正文、图片）
     * @return 三态审核结果，永不返回 null
     */
    public ContentAuditResult review(ContentAuditEvent event) {
        String typeDesc = describeType(event.getContentType());
        auditLog.info("========== AI审核开始 [{}] targetId={} ==========", typeDesc, event.getTargetId());

        String content = buildAuditText(event);
        List<String> imageUrls = event.getImageUrls();
        boolean hasImages = imageUrls != null && !imageUrls.isEmpty();
        auditLog.info("审核内容长度: {} 字符, 图片数量: {}",
                content != null ? content.length() : 0, hasImages ? imageUrls.size() : 0);

        try {
            String url = baseUrl + "/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, Object>> userContent = new ArrayList<>();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", AUDIT_PROMPT + "\n\n待审核内容：\n" +
                    (content != null && !content.isEmpty() ? content : "（无文字内容）"));
            userContent.add(textPart);

            if (hasImages) {
                for (String imgUrl : imageUrls) {
                    Map<String, Object> imagePart = new HashMap<>();
                    imagePart.put("type", "image_url");
                    Map<String, String> urlObj = new HashMap<>();
                    urlObj.put("url", imgUrl);
                    imagePart.put("image_url", urlObj);
                    userContent.add(imagePart);
                }
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.1);
            body.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String reply = root.path("choices").get(0).path("message").path("content").asText();
            String cleanJson = extractJson(reply);

            auditLog.info("AI原始回复: {}", reply);

            JsonNode resultNode = objectMapper.readTree(cleanJson);
            String verdict = normalizeVerdict(resultNode.path("verdict").asText("suspicious"));
            String reason = resultNode.path("reason").asText("未知");
            String category = resultNode.path("category").asText("未知");

            ContentAuditResult result = new ContentAuditResult(verdict, reason, category);

            auditLog.info("审核结果: verdict={}, category={}, reason={}", verdict, category, reason);
            auditLog.info("========== AI审核结束 [{}] targetId={} ==========", typeDesc, event.getTargetId());

            return result;

        } catch (Exception e) {
            auditLog.error("AI审核调用异常: {}", e.getMessage());
            auditLog.info("========== AI审核异常，降级转人工 [{}] targetId={} ==========", typeDesc, event.getTargetId());
            // 关键降级策略：AI 不可用时既不放行也不误删，一律转人工复核
            return new ContentAuditResult(ContentAuditResult.VERDICT_SUSPICIOUS,
                    "AI审核服务异常，转人工复核", "系统异常");
        }
    }

    /** 拼接标题与正文作为待审文本 */
    private String buildAuditText(ContentAuditEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getTitle() != null && !event.getTitle().isEmpty()) {
            sb.append("【标题】").append(event.getTitle()).append("\n");
        }
        if (event.getContent() != null && !event.getContent().isEmpty()) {
            sb.append("【正文】").append(event.getContent());
        }
        return sb.toString();
    }

    /** 规范化 AI 返回的 verdict，非法值一律按 suspicious 处理 */
    private String normalizeVerdict(String verdict) {
        if (verdict == null) {
            return ContentAuditResult.VERDICT_SUSPICIOUS;
        }
        verdict = verdict.trim().toLowerCase();
        if (ContentAuditResult.VERDICT_SAFE.equals(verdict)
                || ContentAuditResult.VERDICT_VIOLATION.equals(verdict)) {
            return verdict;
        }
        return ContentAuditResult.VERDICT_SUSPICIOUS;
    }

    /** 内容类型中文描述（日志用） */
    private String describeType(Integer contentType) {
        if (contentType == null) return "未知";
        return switch (contentType) {
            case ContentAuditEvent.TYPE_POST -> "社区动态";
            case ContentAuditEvent.TYPE_RECIPE -> "食谱";
            case ContentAuditEvent.TYPE_KNOWLEDGE -> "知识文章";
            case ContentAuditEvent.TYPE_POST_COMMENT -> "社区评论";
            case ContentAuditEvent.TYPE_RECIPE_COMMENT -> "食谱评论";
            case ContentAuditEvent.TYPE_KNOWLEDGE_COMMENT -> "知识评论";
            case ContentAuditEvent.TYPE_QUESTION -> "提问";
            case ContentAuditEvent.TYPE_ANSWER -> "回答";
            default -> "未知类型" + contentType;
        };
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        text = text.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
