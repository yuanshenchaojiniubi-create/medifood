package com.medifoodai.controller;

import com.medifood.common.result.Result;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifoodai.service.*;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * AI 智能问答控制器
 * 
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class DeepSeekController {

    private static final Logger ragLog = LoggerFactory.getLogger("RAG");

    private final DeepSeekChatModel chatModel;
    private final ElasticsearchUtil elasticsearchUtil;
    private final com.medifoodai.service.EmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;

    @Autowired(required = false)
    private FoodImageAnalysisService foodImageAnalysisService;

    @Autowired(required = false)
    private ReportAnalysisService reportAnalysisService;

    @Autowired(required = false)
    private FileExtractionService fileExtractionService;

    @Autowired(required = false)
    private DialogueContextService dialogueContextService;

    @Autowired(required = false)
    private UserProfileService userProfileService;

    @Autowired(required = false)
    private UserBehaviorService userBehaviorService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private TokenUsageService tokenUsageService;

    @Value("${spring.ai.deepseek.api-key:NOT_SET}")
    private String configuredApiKey;

    @Autowired
    public DeepSeekController(DeepSeekChatModel chatModel, ElasticsearchUtil elasticsearchUtil,
                              com.medifoodai.service.EmbeddingService embeddingService,
                              RagRetrievalService ragRetrievalService) {
        this.chatModel = chatModel;
        this.elasticsearchUtil = elasticsearchUtil;
        this.embeddingService = embeddingService;
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostConstruct
    public void init() {
        String envKey = System.getenv("MEDIFOOD_DEEPSEEK_API_KEY");
        String maskedKey = configuredApiKey != null && configuredApiKey.length() > 10
                ? configuredApiKey.substring(0, 8) + "..." + configuredApiKey.substring(configuredApiKey.length() - 4)
                : configuredApiKey;
        log.info("🔑 DeepSeek API Key 诊断: env_var_set={}, config_prefix={}, ChatModel={}", 
                envKey != null && !envKey.isBlank(), maskedKey,
                chatModel != null ? "已注入" : "未注入");
    }

    /**
     * 请求消息实体
     */
    public static class ChatRequest {
        private String message;
        private List<MessageItem> history;
        /** 对话 ID（多轮对话时传入，新对话为空） */
        private Long conversationId;
        /** 用户 ID（个性化推荐用） */
        private Long userId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<MessageItem> getHistory() { return history; }
        public void setHistory(List<MessageItem> history) { this.history = history; }
        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    /**
     * 对话历史项
     */
    public static class MessageItem {
        private String role;
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * 普通问答生成（非 RAG）
     */
    @PostMapping("/generate")
    public Result generate(@RequestBody ChatRequest request) {
        return Result.success("generation", chatModel.call(request.getMessage()));
    }

    /**
     * 增强版 RAG 智能问答（流式响应）
     * <p>
     * 使用 RagRetrievalService 统一检索 4 个语义索引，
     * 注入对话历史上下文（多轮对话）和用户画像（个性化推荐）
     * </p>
     */
    @SentinelResource(value = "ai_rag_stream", blockHandler = "handleBlock")
    @PostMapping(value = "/ragStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> ragStream(@RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        String message = request.getMessage();
        List<MessageItem> history = request.getHistory();
        Long conversationId = request.getConversationId();
        Long userId = request.getUserId();

        // ========== 指标记录：开始计时 ==========
        long startTime = System.currentTimeMillis();
        boolean hasRagHit = false;

        try {
            // 使用 RagRetrievalService 统一检索
            RagRetrievalService.RagContext ragContext = ragRetrievalService.retrieve(message);
            hasRagHit = ragContext.hasAny();

            // ========== 指标记录：请求计数 ==========
            if (metricsService != null) {
                metricsService.recordRequest(hasRagHit);
            }

            // ========== RAG 检索日志 ==========
            ragLog.info("========== RAG 检索记录 ==========");
            ragLog.info("用户问题: {}", message);
            ragLog.info("对话ID: {}, 用户ID: {}", conversationId, userId);
            ragLog.info("检索时间: {}", java.time.LocalDateTime.now().toString().replace("T", " "));
            ragLog.info("召回: 知识{}条, 食谱{}条, 问答{}条, 社区{}条, 动态topK={}",
                    ragContext.getKnowledgeHits().size(), ragContext.getRecipeHits().size(),
                    ragContext.getQaHits().size(), ragContext.getCommunityHits().size(),
                    ragRetrievalService.computeTopK(message));

            for (RagRetrievalService.Hit hit : ragContext.getMergedHits()) {
                ragLog.info("  [{}] score={} id={} 标题={}",
                        hit.getSource(), String.format("%.4f", hit.getScore()), hit.getId(), hit.getTitle());
            }
            ragLog.info("========== RAG 检索结束 ==========");

            // ========== 构建对话上下文（阶段 3：多轮对话增强） ==========
            String dialogueContextHeader = "";
            if (dialogueContextService != null && conversationId != null) {
                DialogueContextService.DialogueContext ctx = dialogueContextService.buildContext(conversationId, message);
                if (ctx.getSummary() != null && !ctx.getSummary().isEmpty()) {
                    dialogueContextHeader += "\n【对话历史摘要】：" + ctx.getSummary() + "\n";
                }
                if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
                    dialogueContextHeader += "\n【最近对话】：\n";
                    for (var pair : ctx.getRecentMessages()) {
                        dialogueContextHeader += String.format("- [%s]: %s\n", pair.getRole(), pair.getContent());
                    }
                }
                ragLog.info("对话上下文: 摘要长度={}, 最近消息数={}, 估算Token={}",
                        ctx.getSummary() != null ? ctx.getSummary().length() : 0,
                        ctx.getRecentMessages() != null ? ctx.getRecentMessages().size() : 0,
                        ctx.getEstimatedTokens());
            }

            // ========== 构建用户画像（阶段 4：个性化推荐） ==========
            String userProfileHeader = "";
            if (userId != null) {
                // 获取用户画像
                if (userProfileService != null) {
                    String profile = userProfileService.buildProfileSummary(userId);
                    if (!profile.isEmpty()) {
                        userProfileHeader += "\n【用户画像】" + profile + "\n";
                    }
                }
                // 获取行为偏好
                if (userBehaviorService != null) {
                    String preference = userBehaviorService.buildPreferenceSummary(userId);
                    if (!preference.isEmpty()) {
                        userProfileHeader += "【用户偏好】" + preference + "\n";
                    }
                }
                if (!userProfileHeader.isEmpty()) {
                    ragLog.info("用户个性化信息已注入: userId={}", userId);
                }
            }

            // 构建 System Prompt
            String systemPrompt;
            if (!ragContext.hasAny()) {
                systemPrompt = """
                        你是一个专业的药食同源养生助手。
                        %s
                        %s
                        【重要提示】：当前用户的问题未在知识库、食谱库、问答库和社区中检索到相关内容。

                        请遵循以下准则：
                        1. 请先明确告知用户："很抱歉，我在知识库中没有检索到与您问题直接相关的内容，以下是根据通用养生知识为您提供的建议："
                        2. 基于你的中医养生专业知识给出通用建议。
                        3. 如果提供了用户画像和偏好信息，请根据用户的具体情况提供个性化建议。
                        4. 回答要温暖、专业，富有同理心，避免生硬的指令式语言。
                        5. 保持对话的连贯性，参考对话历史中的上下文。
                        6. 请在结尾处添加"以上为通用养生建议，知识和食谱仅作为养生参考，身体不适请线下就医"的提示词。
                        """.formatted(dialogueContextHeader, userProfileHeader);
            } else {
                StringBuilder contextBuilder = new StringBuilder();
                appendContextHits(contextBuilder, "参考养生知识", ragContext.getKnowledgeHits(), "知识源");
                appendContextHits(contextBuilder, "推荐药食食谱", ragContext.getRecipeHits(), "食谱源");
                appendContextHits(contextBuilder, "相关问答", ragContext.getQaHits(), "问答源");
                appendContextHits(contextBuilder, "社区讨论", ragContext.getCommunityHits(), "社区源");

                systemPrompt = """
                        你是一个专业的药食同源养生助手。
                        %s
                        %s

                        请严格遵循以下准则：
                        1. 优先使用下方【参考信息】中的内容回答用户问题。
                        2. 如果使用了参考信息中的某条知识或食谱，必须在回答中使用 [来源：《标题》] 格式标注出处。
                        3. 如果用户询问食谱，请详细说明其功效、所需食材、制作流程和适合人群。
                        4. 回答要温暖、专业，富有同理心，避免生硬的指令式语言。
                        5. 如果参考信息中有多条相关内容，请尽可能综合引用，并在结尾列出参考文献清单。
                        6. 如果参考信息不足以完全回答用户问题，可以结合专业知识补充，但要明确标注"以下为通用建议"。
                        7. 如果提供了用户画像和偏好信息，请优先推荐与用户体质和偏好匹配的内容。
                        8. 保持对话的连贯性，参考对话历史中的上下文。
                        9. 请在结尾处添加"知识和食谱仅作为养生参考，身体不适请线下就医"的提示词。
                        ---
                        %s
                        ---
                        """.formatted(dialogueContextHeader, userProfileHeader, contextBuilder.toString());
            }

            List<Message> chatMessages = new ArrayList<>();
            chatMessages.add(new SystemMessage(systemPrompt));

            // 注入对话上下文中的最近消息
            if (dialogueContextService != null && conversationId != null) {
                DialogueContextService.DialogueContext ctx = dialogueContextService.buildContext(conversationId, message);
                if (ctx.getRecentMessages() != null) {
                    for (var pair : ctx.getRecentMessages()) {
                        if ("user".equalsIgnoreCase(pair.getRole())) {
                            chatMessages.add(new UserMessage(pair.getContent()));
                        } else if ("assistant".equalsIgnoreCase(pair.getRole())) {
                            chatMessages.add(new AssistantMessage(pair.getContent()));
                        }
                    }
                }
            }

            // 注入前端传来的历史消息（兼容旧版）
            if (history != null && (conversationId == null || dialogueContextService == null)) {
                for (MessageItem item : history) {
                    if ("user".equalsIgnoreCase(item.getRole())) {
                        chatMessages.add(new UserMessage(item.getContent()));
                    } else if ("assistant".equalsIgnoreCase(item.getRole())) {
                        chatMessages.add(new AssistantMessage(item.getContent()));
                    }
                }
            }
            chatMessages.add(new UserMessage(message));

            return chatModel.stream(new Prompt(chatMessages))
                    .doOnNext(chatResponse -> {
                        // 从 ChatResponse 元数据中提取 Token 用量
                        if (chatResponse != null && chatResponse.getMetadata() != null) {
                            try {
                                Object usage = chatResponse.getMetadata().get("usage");
                                if (usage instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> usageMap = (Map<String, Object>) usage;
                                    Object promptTokens = usageMap.get("promptTokens");
                                    Object completionTokens = usageMap.get("generationTokens");
                                    int pTokens = promptTokens instanceof Number ? ((Number) promptTokens).intValue() : 0;
                                    int cTokens = completionTokens instanceof Number ? ((Number) completionTokens).intValue() : 0;
                                    if (pTokens > 0 || cTokens > 0) {
                                        // 暂存 Token 用量到 Redis，等待 appendMessage 时关联 messageId
                                        tokenUsageService.stagePendingUsage(conversationId, pTokens, cTokens);
                                    }
                                }
                            } catch (Exception ex) {
                                log.debug("提取 Token 用量失败: {}", ex.getMessage());
                            }
                        }
                    })
                    .doFinally(signalType -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        if (metricsService != null) {
                            metricsService.recordResponseTime(elapsed);
                        }
                        ragLog.info("RAG 流式回答完成，耗时 {}ms", elapsed);
                    })
                    .onErrorResume(e -> {
                        log.error("AI Stream Error: {}", e.getMessage());
                        ragLog.error("AI流式输出异常: {}", e.getMessage());
                        long elapsed = System.currentTimeMillis() - startTime;
                        if (metricsService != null) {
                            metricsService.recordResponseTime(elapsed);
                        }
                        return Flux.empty();
                    });

        } catch (Exception e) {
            log.error("RAG Process Error: {}", e.getMessage());
            ragLog.error("RAG处理异常: {}", e.getMessage());
            return Flux.error(e);
        }
    }

    /**
     * 将检索命中追加到上下文构建器
     */
    private void appendContextHits(StringBuilder sb, String sectionTitle,
                                    List<RagRetrievalService.Hit> hits, String sourceLabel) {
        if (hits == null || hits.isEmpty()) return;
        sb.append("\n【").append(sectionTitle).append("】:\n");
        for (int i = 0; i < hits.size(); i++) {
            RagRetrievalService.Hit hit = hits.get(i);
            String snippet = hit.getSnippet() != null ? hit.getSnippet() : truncate(hit.getContent(), 800);
            sb.append(String.format("[%s%d] 《%s》(id:%s): %s\n",
                    sourceLabel, i + 1, hit.getTitle(), hit.getId(), snippet));
        }
    }

    /**
     * 辅助方法：截断过长文本
     */
    private String truncate(String str, int length) {
        if (str == null) return "";
        return str.length() > length ? str.substring(0, length) + "..." : str;
    }

    /**
     * 普通流式生成
     */
    @PostMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatModel.stream(new Prompt(new UserMessage(message)));
    }

    /**
     * 初始化 HNSW 向量索引（4 个语义索引）
     * 自动检测维度变化，维度不匹配时删除旧索引重建
     */
    @PostMapping("/semantic/init")
    public Result<?> initSemanticIndices() {
        try {
            int dims = embeddingService.dimensions();
            // 如果索引已存在但维度不匹配，先删除再重建
            elasticsearchUtil.recreateKnnIndexIfDimsMismatch("knowledge_semantic", dims);
            elasticsearchUtil.recreateKnnIndexIfDimsMismatch("recipe_semantic", dims);
            elasticsearchUtil.recreateKnnIndexIfDimsMismatch("qa_semantic", dims);
            elasticsearchUtil.recreateKnnIndexIfDimsMismatch("community_semantic", dims);
            log.info("✅ 语义索引初始化完成: dims={}", dims);
            return Result.success("4 semantic indices initialized (dims=" + dims + ")");
        } catch (Exception e) {
            return Result.fail("init error: " + e.getMessage());
        }
    }

    /**
     * 食物图片智能识别
     * 支持两种模式：ingredient（食材识别）/ dish（菜品分析）
     */
    @SentinelResource(value = "ai_food_analysis", blockHandler = "handleBlockResult")
    @PostMapping("/analyze-food")
    public Result<?> analyzeFood(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "mode", defaultValue = "ingredient") String mode) {
        if (foodImageAnalysisService == null) {
            return Result.fail("食物识别服务未就绪");
        }
        if (file.isEmpty()) {
            return Result.fail("请上传食物图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("仅支持图片格式（jpg/png/webp等）");
        }
        try {
            String imageUrl = fileToDataUrl(file);
            Map<String, Object> result = foodImageAnalysisService.analyzeFood(imageUrl, mode);
            return Result.success(result);
        } catch (Exception e) {
            log.error("食物图片识别失败", e);
            return Result.fail("识别失败: " + e.getMessage());
        }
    }

    /**
     * 体检报告智能分析
     * 支持 PDF / Word / TXT / 图片格式
     */
    @SentinelResource(value = "ai_report_analysis", blockHandler = "handleBlockResult")
    @PostMapping("/analyze-report")
    public Result<?> analyzeReport(@RequestParam("file") MultipartFile file) {
        if (reportAnalysisService == null) {
            return Result.fail("报告分析服务未就绪");
        }
        if (file.isEmpty()) {
            return Result.fail("请上传体检报告文件");
        }
        try {
            String reportText;
            String contentType = file.getContentType();

            if (contentType != null && contentType.startsWith("image/")) {
                String imageUrl = fileToDataUrl(file);
                Map<String, Object> ocrResult = foodImageAnalysisService != null
                        ? foodImageAnalysisService.analyzeFood(imageUrl, "ocr")
                        : Map.of("ocrText", "");
                Object text = ocrResult.get("ocrText");
                reportText = text != null ? text.toString() : "";
            } else if (fileExtractionService != null && fileExtractionService.isSupportedFile(
                    file.getOriginalFilename())) {
                reportText = fileExtractionService.extract(file);
            } else {
                return Result.fail("不支持的文件格式，请上传 PDF / Word / TXT / 图片文件");
            }

            if (reportText == null || reportText.isBlank()) {
                return Result.fail("未能从文件中提取到文字内容，请确保文件包含可读文本");
            }

            Map<String, Object> result = reportAnalysisService.analyzeReport(reportText);
            return Result.success(result);
        } catch (Exception e) {
            log.error("体检报告分析失败", e);
            return Result.fail("分析失败: " + e.getMessage());
        }
    }

    private String fileToDataUrl(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        if (contentType == null) contentType = "image/jpeg";
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        return "data:" + contentType + ";base64," + base64;
    }

    /**
     * 索引一条语义文档（便于构建/测试）
     */
    @PostMapping("/semantic/indexDoc")
    public Result<?> indexSemanticDoc(@RequestBody Map<String, Object> payload) {
        String type = String.valueOf(payload.getOrDefault("type", "knowledge"));
        String id = String.valueOf(payload.getOrDefault("id", ""));
        String title = String.valueOf(payload.getOrDefault("title", ""));
        String content = String.valueOf(payload.getOrDefault("content", ""));
        if (id == null || id.isEmpty()) {
            return Result.fail("id required");
        }
        String index = "knowledge".equalsIgnoreCase(type) ? "knowledge_semantic" : "recipe_semantic";
        String text = (title == null ? "" : title) + " " + (content == null ? "" : content);
        float[] vec = embeddingService.embed(text);
        Map<String, Object> src = new HashMap<>();
        src.put("id", id);
        src.put("title", title);
        src.put("content", content);
        boolean ok = elasticsearchUtil.indexDocumentWithVector(index, id, src, vec);
        return ok ? Result.success("ok") : Result.fail("index failed");
    }

    /**
     * 图片 + 文字智能问答（阶段 8：图片内联聊天）
     * <p>
     * 先调用 FoodImageAnalysisService 识别图片内容，
     * 再将识别结果作为上下文注入 RAG 问答流程，
     * 支持对话系统集成（conversationId 可选）。
     * </p>
     */
    @SentinelResource(value = "ai_image_analysis", blockHandler = "handleBlockResult")
    @PostMapping("/chat-with-image")
    public Result<?> chatWithImage(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "message", defaultValue = "") String message,
                                   @RequestParam(value = "mode", defaultValue = "ingredient") String mode,
                                   @RequestParam(value = "conversationId", required = false) Long conversationId) {
        if (foodImageAnalysisService == null) {
            return Result.fail("食物识别服务未就绪");
        }
        if (file.isEmpty()) {
            return Result.fail("请上传图片");
        }

        try {
            // 1. 识别图片内容
            String imageUrl = fileToDataUrl(file);
            Map<String, Object> analysis = foodImageAnalysisService.analyzeFood(imageUrl, mode);

            // 2. 构建增强问题
            StringBuilder enhancedMessage = new StringBuilder();
            if (message != null && !message.isEmpty()) {
                enhancedMessage.append(message).append("\n\n");
            }
            enhancedMessage.append("【图片识别结果】\n").append(toJson(analysis));

            // 3. 使用 RAG 检索获取相关知识
            String queryText = enhancedMessage.toString();
            RagRetrievalService.RagContext ragContext = ragRetrievalService.retrieve(queryText);

            // 4. 构建对话上下文（如果有 conversationId）
            String dialogueContextHeader = "";
            if (dialogueContextService != null && conversationId != null) {
                DialogueContextService.DialogueContext ctx = dialogueContextService.buildContext(conversationId, queryText);
                if (ctx.getSummary() != null && !ctx.getSummary().isEmpty()) {
                    dialogueContextHeader += "\n【对话历史摘要】：" + ctx.getSummary() + "\n";
                }
                if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
                    dialogueContextHeader += "\n【最近对话】：\n";
                    for (var pair : ctx.getRecentMessages()) {
                        dialogueContextHeader += String.format("- [%s]: %s\n", pair.getRole(), pair.getContent());
                    }
                }
            }

            // 5. 构建 System Prompt
            StringBuilder contextBuilder = new StringBuilder();
            appendContextHits(contextBuilder, "参考养生知识", ragContext.getKnowledgeHits(), "知识源");
            appendContextHits(contextBuilder, "推荐药食食谱", ragContext.getRecipeHits(), "食谱源");

            String systemPrompt = """
                    你是一个专业的药食同源养生助手，具备图片食材识别能力。
                    %s

                    请基于以下【图片识别结果】和【知识库参考信息】，为用户提供专业分析：
                    1. 首先确认图片中识别出的食材/菜品
                    2. 分析其药食同源功效
                    3. 推荐相关的食谱和食用建议
                    4. 如果用户有额外问题，请一并回答
                    5. 保持对话的连贯性，参考对话历史中的上下文

                    ---
                    %s
                    ---
                    请在结尾处添加"知识和食谱仅作为养生参考，身体不适请线下就医"的提示词。
                    """.formatted(dialogueContextHeader, contextBuilder.toString());

            List<Message> chatMessages = new ArrayList<>();
            chatMessages.add(new SystemMessage(systemPrompt));

            // 注入对话上下文中的最近消息
            if (dialogueContextService != null && conversationId != null) {
                DialogueContextService.DialogueContext ctx = dialogueContextService.buildContext(conversationId, queryText);
                if (ctx.getRecentMessages() != null) {
                    for (var pair : ctx.getRecentMessages()) {
                        if ("user".equalsIgnoreCase(pair.getRole())) {
                            chatMessages.add(new UserMessage(pair.getContent()));
                        } else if ("assistant".equalsIgnoreCase(pair.getRole())) {
                            chatMessages.add(new AssistantMessage(pair.getContent()));
                        }
                    }
                }
            }
            chatMessages.add(new UserMessage(enhancedMessage.toString()));

            ChatResponse response = chatModel.call(new Prompt(chatMessages));
            String answer = response.getResult().getOutput().getText();

            // 6. 如果有关联对话，自动保存消息
            Long savedMessageId = null;
            if (conversationId != null && dialogueContextService == null) {
                // 仅在没有 DialogueContextService 时尝试直接保存（有 DialogueContextService 时由前端保存）
            }

            Map<String, Object> result = new HashMap<>();
            result.put("analysis", analysis);
            result.put("answer", answer);
            result.put("success", true);
            result.put("messageId", savedMessageId);

            return Result.success(result);

        } catch (Exception e) {
            log.error("图片聊天分析失败: {}", e.getMessage(), e);
            return Result.fail("分析失败: " + e.getMessage());
        }
    }

    // ========== Sentinel 限流/熔断降级处理 ==========

    /**
     * 处理 Flux 流式接口的限流/熔断降级
     */
    public Flux<ChatResponse> handleBlock(ChatRequest request, HttpServletResponse response, BlockException e) {
        log.warn("AI 流式接口被 Sentinel 拦截: resource={}, rule={}", e.getRule().getResource(), e.getRule());
        AssistantMessage fallbackMsg = new AssistantMessage(
                "【提示】当前 AI 服务请求过于频繁，请稍后再试。" +
                "（系统限流保护中，异常类型：" + e.getClass().getSimpleName() + "）"
        );
        return Flux.just(new ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(fallbackMsg)
        )));
    }

    /**
     * 处理普通 Result 接口的限流/熔断降级
     */
    public Result<?> handleBlockResult(Object... args) {
        // 提取 BlockException（最后一个参数）
        BlockException e = null;
        for (Object arg : args) {
            if (arg instanceof BlockException) {
                e = (BlockException) arg;
                break;
            }
        }
        String resource = e != null ? e.getRule().getResource() : "unknown";
        log.warn("AI 接口被 Sentinel 拦截: resource={}", resource);

        if (e instanceof com.alibaba.csp.sentinel.slots.block.degrade.DegradeException) {
            return Result.fail(429, "AI 服务暂时不可用，正在恢复中（熔断保护），请稍后再试");
        }
        return Result.fail(429, "请求过于频繁，请稍后再试（限流保护）");
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
