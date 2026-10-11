package com.medifoodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifoodai.tool.BMICalculatorTool;
import com.medifoodai.tool.FoodCompatibilityTool;
import com.medifoodai.tool.IngredientLookupTool;
import com.medifoodai.tool.KnowledgeSearchTool;
import com.medifoodai.tool.QASearchTool;
import com.medifoodai.tool.RecipeDetailTool;
import com.medifoodai.tool.RecipeSearchTool;
import com.medifoodai.tool.SemanticSearchTool;
import com.medifoodai.tool.UserProfileTool;
import com.medifoodai.tool.WeatherQueryTool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ReAct Agent 服务
 * <p>
 * 实现 ReAct（Reasoning + Acting）循环，让 AI 自主决策调用哪些工具来回答问题。
 * </p>
 * <p>
 * 循环流程：
 * 1. Thought（思考）：LLM 分析问题，决定是否需要调用工具
 * 2. Action（行动）：执行工具调用（semantic_search / recipe_search / knowledge_search / weather_query）
 * 3. Observation（观察）：将工具返回结果反馈给 LLM
 * 4. 重复 1-3，直到 LLM 给出最终答案（最多 3 轮）
 * </p>
 * <p>
 * 支持 10 个工具：
 * - semantic_search：语义检索（知识库+食谱+问答+社区）
 * - recipe_search：关键词搜索食谱
 * - knowledge_search：关键词搜索知识
 * - weather_query：查询节气/天气/饮食建议
 * - user_profile：获取用户体质、偏好信息（个性化）
 * - bmi_calculator：根据身高体重计算 BMI
 * - recipe_detail：根据ID获取完整食谱详情
 * - ingredient_lookup：查询食材药性、功效、禁忌
 * - food_compatibility：检查两种食物搭配是否合适
 * - qa_search：搜索问答库中类似问题
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class ReActAgentService {

    @Autowired
    private DeepSeekChatModel chatModel;

    @Autowired
    private SemanticSearchTool semanticSearchTool;

    @Autowired
    private RecipeSearchTool recipeSearchTool;

    @Autowired
    private KnowledgeSearchTool knowledgeSearchTool;

    @Autowired
    private WeatherQueryTool weatherQueryTool;

    @Autowired
    private UserProfileTool userProfileTool;

    @Autowired
    private BMICalculatorTool bmiCalculatorTool;

    @Autowired
    private RecipeDetailTool recipeDetailTool;

    @Autowired
    private IngredientLookupTool ingredientLookupTool;

    @Autowired
    private FoodCompatibilityTool foodCompatibilityTool;

    @Autowired
    private QASearchTool qaSearchTool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大工具调用轮数 */
    private static final int MAX_ROUNDS = 3;

    /**
     * 单次请求的上下文（线程安全修复）
     * <p>
     * 本类为 Spring 单例 Bean，请求级状态（用户ID/经纬度）绝不能存为实例字段，
     * 否则并发请求会互相覆盖，导致用户画像串号（详见 executeTool 的注入点）。
     * 因此封装为局部对象，随调用链传递，每个线程持有自己的副本。
     * </p>
     */
    @Data
    public static class AgentContext {
        /** 当前请求的用户 ID（用于个性化工具，非 LLM 参数） */
        private final Long userId;
        /** 用户当前纬度（用于天气工具，非 LLM 参数） */
        private final Double userLat;
        /** 用户当前经度（用于天气工具，非 LLM 参数） */
        private final Double userLng;

        public AgentContext(Long userId, Double userLat, Double userLng) {
            this.userId = userId;
            this.userLat = userLat;
            this.userLng = userLng;
        }
    }

    @Data
    public static class AgentStep {
        /** 本轮思考 */
        private String thought;
        /** 工具名称 */
        private String toolName;
        /** 工具参数 */
        private Map<String, Object> toolParams;
        /** 工具返回结果 */
        private Map<String, Object> toolResult;
        /** 是否为最终答案 */
        private boolean finalAnswer;
        /** 最终答案文本 */
        private String answer;
    }

    @Data
    public static class AgentResult {
        /** 是否成功 */
        private boolean success;
        /** 最终答案 */
        private String answer;
        /** 执行步骤记录 */
        private List<AgentStep> steps;
        /** 总轮数 */
        private int totalRounds;
        /** 使用的工具清单 */
        private List<String> toolsUsed;
    }

    /**
     * ReAct Agent 非流式问答
     *
     * @param userId 用户 ID（用于个性化工具，可为 null）
     */
    public AgentResult react(String userMessage, List<Map<String, String>> history, Long userId, Double lat, Double lng) {
        // 请求级状态封装为局部对象（线程私有），随调用链传递，避免单例实例字段被并发请求覆盖
        AgentContext ctx = new AgentContext(userId, lat, lng);
        AgentResult result = new AgentResult();
        result.setSteps(new ArrayList<>());

        List<Message> messages = buildInitialMessages(userMessage, history);

        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                log.info("ReAct Agent 第 {} 轮思考...", round + 1);

                // 请求 LLM 决策
                ChatResponse response = chatModel.call(new Prompt(messages));
                String content = response.getResult().getOutput().getText();

                // 解析 LLM 决策
                AgentStep step = parseDecision(content);
                if (step == null) {
                    // 解析失败，当作最终答案
                    result.setAnswer(content);
                    result.setSuccess(true);
                    result.setTotalRounds(round + 1);
                    result.setToolsUsed(getToolsUsed(result.getSteps()));
                    return result;
                }

                result.getSteps().add(step);

                if (step.isFinalAnswer()) {
                    result.setAnswer(step.getAnswer());
                    result.setSuccess(true);
                    result.setTotalRounds(round + 1);
                    result.setToolsUsed(getToolsUsed(result.getSteps()));
                    log.info("ReAct Agent 完成：{} 轮，使用工具：{}", round + 1, result.getToolsUsed());
                    return result;
                }

                // 执行工具调用
                Map<String, Object> toolResult = executeTool(step.getToolName(), step.getToolParams(), ctx);
                step.setToolResult(toolResult);

                // 将观察结果反馈给 LLM
                messages.add(new AssistantMessage(content));
                messages.add(new UserMessage("【工具调用结果】\n" + toJson(toolResult) +
                        "\n\n请基于以上工具返回的结果，继续回答用户问题。如果信息已足够，请直接给出最终答案。如果还需要更多信息，请继续调用工具。"));
            }

            // 超过最大轮数，强制要求最终答案
            messages.add(new UserMessage("已超过最大工具调用次数，请基于已有信息直接给出最终答案。"));
            ChatResponse finalResponse = chatModel.call(new Prompt(messages));
            result.setAnswer(finalResponse.getResult().getOutput().getText());
            result.setSuccess(true);
            result.setTotalRounds(MAX_ROUNDS);
            result.setToolsUsed(getToolsUsed(result.getSteps()));
            log.info("ReAct Agent 达到最大轮数 {}，强制结束", MAX_ROUNDS);

        } catch (Exception e) {
            log.error("ReAct Agent 执行异常: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setAnswer("抱歉，AI 助手处理您的问题时遇到了错误，请稍后重试。");
        }

        return result;
    }

    /**
     * ReAct Agent 流式问答
     * <p>
     * 先执行工具调用（非流式），再流式输出最终答案
     * </p>
     *
     * @param userId 用户 ID（用于个性化工具，可为 null）
     */
    public Flux<ChatResponse> reactStream(String userMessage, List<Map<String, String>> history, Long userId, Double lat, Double lng) {
        // 先执行 ReAct 循环，收集上下文
        AgentResult agentResult = react(userMessage, history, userId, lat, lng);

        if (!agentResult.isSuccess()) {
            return Flux.error(new RuntimeException(agentResult.getAnswer()));
        }

        // 构建最终消息列表（包含工具调用上下文）
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                你是药食同源养生助手。请基于以下对话历史和工具调用结果，给出专业、温暖、结构清晰的回答。

                请遵循以下准则：
                1. 优先使用工具调用返回的信息回答用户问题
                2. 回答要温暖、专业，富有同理心
                3. 如果涉及食谱，请详细说明功效、食材、做法
                4. 请在结尾处添加"知识和食谱仅作为养生参考，身体不适请线下就医"的提示词
                """));

        if (history != null) {
            for (Map<String, String> item : history) {
                String role = item.get("role");
                String content = item.get("content");
                if ("user".equalsIgnoreCase(role)) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(role)) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        // 添加工具调用结果作为上下文
        StringBuilder toolContext = new StringBuilder();
        for (AgentStep step : agentResult.getSteps()) {
            if (step.getToolResult() != null) {
                toolContext.append("【").append(step.getToolName()).append("】结果：")
                        .append(toJson(step.getToolResult())).append("\n\n");
            }
        }
        if (!toolContext.isEmpty()) {
            messages.add(new SystemMessage("工具调用结果：\n" + toolContext));
        }

        messages.add(new UserMessage(userMessage));

        return chatModel.stream(new Prompt(messages))
                .onErrorResume(e -> {
                    log.error("ReAct Agent 流式输出异常: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    // ==================== 私有方法 ====================

    private List<Message> buildInitialMessages(String userMessage, List<Map<String, String>> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                你是药食同源养生助手，具备以下工具能力：

                一、搜索类工具：
                1. semantic_search(query, topK) - 语义检索：在知识库、食谱库、问答库、社区中搜索相关内容
                2. recipe_search(keyword, topN) - 食谱搜索：按关键词搜索具体食谱
                3. knowledge_search(keyword, topN) - 知识搜索：按关键词搜索养生知识
                4. qa_search(keyword, topN) - 问答搜索：专门搜索问答库中类似问题

                二、个性化工具：
                5. user_profile() - 获取当前用户画像：包括体质、性别、健康目标等。当用户问的问题跟"我"有关时优先调用（无需参数）
                6. bmi_calculator(height, weight) - BMI计算：根据身高(cm)和体重(kg)计算BMI，给出健康评估

                三、详情类工具：
                7. recipe_detail(recipeId) - 食谱详情：根据食谱ID获取完整做法步骤
                8. ingredient_lookup(ingredient) - 食材查询：查询某种食材的药性、功效、禁忌

                四、辅助类工具：
                9. food_compatibility(food1, food2) - 食物搭配：检查两种食材能否一起吃
                10. weather_query(city, lat, lng) - 天气查询：查询用户所在地的实时天气+节气信息。参数均可选，推荐传入经纬度(lat/lng)获取精准天气。

                请按照以下 ReAct 格式思考并回答（不要输出其他内容）：

                Thought: 你的思考过程，分析用户问题，判断需要调用哪个工具
                Action: 需要调用的工具名称（如 user_profile, bmi_calculator, semantic_search 等）
                Action Input: {"参数名": "参数值"}  （JSON格式）

                如果不需要调用工具或信息已足够，请输出：
                Thought: 信息已足够，直接回答
                Final Answer: 你的最终回答

                重要规则：
                - 每次只调用一个工具
                - 用户问"我适合吃什么""根据我的体质推荐"等涉及个人情况时，必须先调用 user_profile
                - 用户提到身高体重要求评估健康时，使用 bmi_calculator
                - 用户问"XX食材有什么功效/禁忌"时，使用 ingredient_lookup
                - 用户问"XX和YY能一起吃吗"时，使用 food_compatibility
                - 用户要求查看某个食谱的详细做法时，使用 recipe_detail
                - 用户问"有没有类似的问题/怎么解决"时，使用 qa_search
                - 涉及食谱推荐，使用 recipe_search 或 semantic_search
                - 涉及节气养生或天气相关饮食推荐，使用 weather_query（优先用经纬度定位）
                - 涉及养生知识，使用 knowledge_search 或 semantic_search
                - 用户问"今天适合吃什么""根据天气推荐"时，必须调用 weather_query
                - 最多调用 3 次工具
                """));

        if (history != null) {
            for (Map<String, String> item : history) {
                String role = item.get("role");
                String content = item.get("content");
                if ("user".equalsIgnoreCase(role)) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(role)) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 解析 LLM 的 ReAct 决策
     */
    private AgentStep parseDecision(String content) {
        if (content == null || content.isEmpty()) return null;

        AgentStep step = new AgentStep();

        // 提取 Thought
        String thought = extractSection(content, "Thought:", "Action:", "Final Answer:");
        step.setThought(thought != null ? thought.trim() : "");

        // 检查是否为最终答案
        if (content.contains("Final Answer:")) {
            String finalAnswer = extractSection(content, "Final Answer:", null, null);
            step.setFinalAnswer(true);
            step.setAnswer(finalAnswer != null ? finalAnswer.trim() : content.trim());
            return step;
        }

        // 提取 Action
        String actionLine = extractSection(content, "Action:", "Action Input:", "Thought:");
        if (actionLine == null) {
            // 没有 Action，也没有 Final Answer，当作最终答案
            step.setFinalAnswer(true);
            step.setAnswer(content.trim());
            return step;
        }
        step.setToolName(actionLine.trim().toLowerCase());

        // 提取 Action Input
        String actionInput = extractSection(content, "Action Input:", "Thought:", "Action:");
        if (actionInput != null) {
            try {
                // 清理 JSON
                actionInput = actionInput.trim();
                int start = actionInput.indexOf('{');
                int end = actionInput.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    actionInput = actionInput.substring(start, end + 1);
                }
                step.setToolParams(objectMapper.readValue(actionInput, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("解析 Action Input 失败: {}", actionInput);
                step.setToolParams(new HashMap<>());
            }
        } else {
            step.setToolParams(new HashMap<>());
        }

        step.setFinalAnswer(false);
        return step;
    }

    /**
     * 提取文本中两个标记之间的内容
     */
    private String extractSection(String text, String startMarker, String... endMarkers) {
        int startIdx = text.indexOf(startMarker);
        if (startIdx < 0) return null;

        startIdx += startMarker.length();
        int endIdx = text.length();

        for (String endMarker : endMarkers) {
            if (endMarker == null) continue;
            int idx = text.indexOf(endMarker, startIdx);
            if (idx >= 0 && idx < endIdx) {
                endIdx = idx;
            }
        }

        return text.substring(startIdx, endIdx);
    }

    /**
     * 执行工具调用
     *
     * @param ctx 当前请求上下文（用户ID/经纬度），由 react() 创建并传递，线程私有
     */
    private Map<String, Object> executeTool(String toolName, Map<String, Object> params, AgentContext ctx) {
        log.info("ReAct Agent 执行工具: {} 参数: {}", toolName, params);
        try {
            return switch (toolName) {
                case "semantic_search" -> {
                    String query = getStringParam(params, "query", "");
                    Integer topK = getIntParam(params, "topK", 4);
                    yield semanticSearchTool.semanticSearch(query, topK);
                }
                case "recipe_search" -> {
                    String keyword = getStringParam(params, "keyword", "");
                    Integer topN = getIntParam(params, "topN", 5);
                    yield recipeSearchTool.recipeSearch(keyword, topN);
                }
                case "knowledge_search" -> {
                    String keyword = getStringParam(params, "keyword", "");
                    Integer topN = getIntParam(params, "topN", 5);
                    yield knowledgeSearchTool.knowledgeSearch(keyword, topN);
                }
                case "weather_query" -> {
                    String city = getStringParam(params, "city", "北京");
                    Double lat = getDoubleParam(params, "lat");
                    Double lng = getDoubleParam(params, "lng");
                    // LLM 传了经纬度就用 LLM 的，否则用服务端注入的用户位置（从本次请求的上下文读取）
                    if (lat == null || lng == null) {
                        lat = ctx.getUserLat();
                        lng = ctx.getUserLng();
                    }
                    yield weatherQueryTool.weatherQuery(city, lat, lng);
                }
                // ===== 新增工具 =====
                case "user_profile" -> {
                    // user_profile 不需要 LLM 参数，userId 从本次请求的上下文注入（非实例字段，避免并发串号）
                    yield userProfileTool.getUserProfile(ctx.getUserId());
                }
                case "bmi_calculator" -> {
                    Double height = getDoubleParam(params, "height");
                    Double weight = getDoubleParam(params, "weight");
                    yield bmiCalculatorTool.bmiCalculator(height, weight);
                }
                case "recipe_detail" -> {
                    String recipeId = getStringParam(params, "recipeId", "");
                    yield recipeDetailTool.recipeDetail(recipeId);
                }
                case "ingredient_lookup" -> {
                    String ingredient = getStringParam(params, "ingredient", "");
                    yield ingredientLookupTool.ingredientLookup(ingredient);
                }
                case "food_compatibility" -> {
                    String food1 = getStringParam(params, "food1", "");
                    String food2 = getStringParam(params, "food2", "");
                    yield foodCompatibilityTool.foodCompatibility(food1, food2);
                }
                case "qa_search" -> {
                    String keyword = getStringParam(params, "keyword", "");
                    Integer topN = getIntParam(params, "topN", 5);
                    yield qaSearchTool.qaSearch(keyword, topN);
                }
                default -> {
                    log.warn("未知工具: {}", toolName);
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "未知工具: " + toolName);
                    yield error;
                }
            };
        } catch (Exception e) {
            log.error("工具执行失败: {} - {}", toolName, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "工具执行失败: " + e.getMessage());
            return error;
        }
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object val = params.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Integer getIntParam(Map<String, Object> params, String key, Integer defaultValue) {
        Object val = params.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Double getDoubleParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> getToolsUsed(List<AgentStep> steps) {
        return steps.stream()
                .filter(s -> s.getToolName() != null)
                .map(AgentStep::getToolName)
                .distinct()
                .collect(Collectors.toList());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}