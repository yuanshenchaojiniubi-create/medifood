package com.medifoodai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模块 API 密钥配置
 * 优先从环境变量读取，无环境变量时回退到 application.yml 默认值
 *
 * 环境变量：
 *   MEDIFOOD_DEEPSEEK_API_KEY       - DeepSeek API 密钥
 *   MEDIFOOD_SILICONFLOW_API_KEY    - SiliconFlow API 密钥
 *   MEDIFOOD_SILICONFLOW_BASE_URL   - SiliconFlow 基础 URL
 *   MEDIFOOD_SILICONFLOW_VISION_MODEL - SiliconFlow 视觉模型
 *   MEDIFOOD_SILICONFLOW_EMBEDDING_MODEL - SiliconFlow Embedding 模型
 *
 * @author MediFood Team
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "medi-food.ai")
public class ApiKeyConfig {

    /** DeepSeek API 密钥 */
    private String deepseekApiKey;

    /** SiliconFlow API 密钥 */
    private String siliconflowApiKey;

    /** SiliconFlow 基础 URL */
    private String siliconflowBaseUrl = "https://api.siliconflow.cn";

    /** SiliconFlow 视觉模型 */
    private String siliconflowVisionModel = "Qwen/Qwen3-VL-30B-A3B-Instruct";

    /** SiliconFlow Embedding 模型 */
    private String siliconflowEmbeddingModel = "BAAI/bge-large-zh-v1.5";

    @PostConstruct
    public void validate() {
        // 优先从环境变量读取
        overrideFromEnv();

        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            log.warn("⚠️ DeepSeek API Key 未配置！请设置环境变量 MEDIFOOD_DEEPSEEK_API_KEY");
        } else {
            log.info("✅ DeepSeek API Key 已配置 (来源: {})",
                    System.getenv("MEDIFOOD_DEEPSEEK_API_KEY") != null ? "环境变量" : "配置文件");
        }

        if (siliconflowApiKey == null || siliconflowApiKey.isBlank()) {
            log.warn("⚠️ SiliconFlow API Key 未配置！请设置环境变量 MEDIFOOD_SILICONFLOW_API_KEY");
        } else {
            log.info("✅ SiliconFlow API Key 已配置 (来源: {})",
                    System.getenv("MEDIFOOD_SILICONFLOW_API_KEY") != null ? "环境变量" : "配置文件");
        }

        log.info("SiliconFlow 配置: baseUrl={}, visionModel={}, embeddingModel={}",
                siliconflowBaseUrl, siliconflowVisionModel, siliconflowEmbeddingModel);
    }

    /**
     * 环境变量优先级高于配置文件
     */
    private void overrideFromEnv() {
        String envDeepseek = System.getenv("MEDIFOOD_DEEPSEEK_API_KEY");
        if (envDeepseek != null && !envDeepseek.isBlank()) {
            this.deepseekApiKey = envDeepseek;
        }

        String envSiliconflow = System.getenv("MEDIFOOD_SILICONFLOW_API_KEY");
        if (envSiliconflow != null && !envSiliconflow.isBlank()) {
            this.siliconflowApiKey = envSiliconflow;
        }

        String envBaseUrl = System.getenv("MEDIFOOD_SILICONFLOW_BASE_URL");
        if (envBaseUrl != null && !envBaseUrl.isBlank()) {
            this.siliconflowBaseUrl = envBaseUrl;
        }

        String envVisionModel = System.getenv("MEDIFOOD_SILICONFLOW_VISION_MODEL");
        if (envVisionModel != null && !envVisionModel.isBlank()) {
            this.siliconflowVisionModel = envVisionModel;
        }

        String envEmbeddingModel = System.getenv("MEDIFOOD_SILICONFLOW_EMBEDDING_MODEL");
        if (envEmbeddingModel != null && !envEmbeddingModel.isBlank()) {
            this.siliconflowEmbeddingModel = envEmbeddingModel;
        }
    }
}