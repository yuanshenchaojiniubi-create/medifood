package com.medifoodai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifoodai.config.ApiKeyConfig;
import com.medifoodai.service.EmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Primary
public class SiliconFlowEmbeddingService implements EmbeddingService {

    private static final int DIMS = 1024;

    private static final String QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章：";

    @Autowired
    private ApiKeyConfig apiKeyConfig;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String apiKey;
    private String baseUrl;
    private String model;

    /** 是否已配置 API Key，未配置时返回零向量，不抛异常 */
    private volatile boolean configured = false;

    /** 防止重复日志刷屏 */
    private volatile boolean warnedOnce = false;

    @org.springframework.beans.factory.annotation.Value("${siliconflow.embedding.max-input-length:512}")
    private int maxInputLength;

    /** Embedding 缓存 TTL：24 小时 */
    private static final long EMB_CACHE_TTL_HOURS = 24;
    private static final String EMB_CACHE_PREFIX = "ai:emb:";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.apiKey = apiKeyConfig.getSiliconflowApiKey();
        this.baseUrl = apiKeyConfig.getSiliconflowBaseUrl();
        this.model = apiKeyConfig.getSiliconflowEmbeddingModel();

        if (apiKey != null && !apiKey.isBlank()) {
            this.configured = true;
            log.info("SiliconFlow Embedding 初始化: baseUrl={}, model={}, dims={}", baseUrl, model, DIMS);
        } else {
            log.warn("⚠️ SiliconFlow API Key 未配置，向量化功能已禁用（设置环境变量 MEDIFOOD_SILICONFLOW_API_KEY 后启用）");
        }
    }

    /**
     * 文档向量化 —— 知识/食谱存入向量库时调用，不加 BGE 查询前缀
     */
    @Override
    public float[] embed(String text) {
        return callApi(text, false);
    }

    /**
     * 查询向量化 —— 用户 RAG 检索时调用，加 BGE 指令前缀
     */
    @Override
    public float[] embedForQuery(String text) {
        return callApi(text, true);
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public String modelName() {
        return model;
    }

    private float[] callApi(String text, boolean isQuery) {
        if (text == null || text.isEmpty()) {
            return new float[DIMS];
        }

        // ========== 未配置 API Key 时返回零向量，不抛异常 ==========
        if (!configured) {
            if (!warnedOnce) {
                warnedOnce = true;
                log.warn("SiliconFlow Embedding 未配置 API Key，向量化请求已跳过（仅警告一次）");
            }
            return new float[DIMS];
        }

        String truncated = truncateText(text, maxInputLength);
        String input = isQuery ? (QUERY_INSTRUCTION + truncated) : truncated;

        // ========== Embedding 缓存层 ==========
        String cacheKey = EMB_CACHE_PREFIX + md5(input);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                // 从缓存中还原 float[] 数组
                String cachedStr = cached.toString();
                String[] parts = cachedStr.split(",");
                float[] vec = new float[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    vec[i] = Float.parseFloat(parts[i]);
                }
                log.debug("Embedding 缓存命中: text={}", truncateText(text, 30));
                return vec;
            }
        } catch (Exception e) {
            log.debug("读取 Embedding 缓存失败，回退到 API 调用: {}", e.getMessage());
        }

        // ========== API 调用 ==========
        try {
            String url = baseUrl + "/v1/embeddings";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", input);
            body.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            float[] vec = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vec[i] = (float) embeddingNode.get(i).asDouble();
            }

            float[] normalized = l2Normalize(vec);

            // ========== 存入缓存 ==========
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < normalized.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(normalized[i]);
                }
                redisTemplate.opsForValue().set(cacheKey, sb.toString(), EMB_CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.debug("存储 Embedding 缓存失败: {}", e.getMessage());
            }

            return normalized;

        } catch (Exception e) {
            log.error("SiliconFlow Embedding 调用失败: isQuery={}, text={}, error={}",
                    isQuery, truncateText(text, 50), e.getMessage());
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    private String truncateText(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private float[] l2Normalize(float[] vec) {
        float norm = 0f;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(Math.max(norm, 1e-12));
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}