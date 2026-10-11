package com.medifoodai.controller;

import com.medifood.common.result.Result;
import com.medifoodai.service.ReActAgentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 控制器
 * <p>
 * 提供 ReAct 模式的智能问答：
 * - /react：非流式，返回完整答案 + 工具调用步骤
 * - /reactStream：流式，先执行工具调用，再流式输出最终答案
 * </p>
 * <p>
 * ReAct Agent 可用工具（共10个）：
 * - 搜索类：semantic_search, recipe_search, knowledge_search, qa_search
 * - 个性化：user_profile, bmi_calculator
 * - 详情类：recipe_detail, ingredient_lookup
 * - 辅助类：food_compatibility, weather_query
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
public class AgentController {

    @Autowired
    private ReActAgentService reactAgentService;

    public static class AgentRequest {
        private String message;
        private Long userId;
        private Double lat;
        private Double lng;
        private List<MessageItem> history;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
        public List<MessageItem> getHistory() { return history; }
        public void setHistory(List<MessageItem> history) { this.history = history; }
    }

    public static class MessageItem {
        private String role;
        private String content;
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * ReAct Agent 非流式问答
     * <p>
     * 返回完整的 Agent 执行过程，包括：
     * - 各轮思考（Thought）
     * - 工具调用和结果
     * - 最终答案
     * </p>
     */
    @PostMapping("/react")
    public Result<Map<String, Object>> react(@RequestBody AgentRequest req) {
        log.info("ReAct Agent 非流式请求: {}", req.getMessage());

        try {
            List<Map<String, String>> history = convertHistory(req.getHistory());

            ReActAgentService.AgentResult result = reactAgentService.react(req.getMessage(), history, req.getUserId(), req.getLat(), req.getLng());

            Map<String, Object> response = new HashMap<>();
            response.put("answer", result.getAnswer());
            response.put("steps", result.getSteps());
            response.put("totalRounds", result.getTotalRounds());
            response.put("toolsUsed", result.getToolsUsed());

            return Result.success(response);
        } catch (Exception e) {
            log.error("ReAct Agent 非流式执行异常: {}", e.getMessage(), e);
            return Result.fail("Agent 执行失败: " + e.getMessage());
        }
    }

    /**
     * ReAct Agent 流式问答
     * <p>
     * 先执行工具调用（非流式），收集上下文后流式输出最终答案。
     * 响应格式：SSE（Server-Sent Events）
     * </p>
     */
    @PostMapping(value = "/reactStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> reactStream(@RequestBody AgentRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        log.info("ReAct Agent 流式请求: {}", req.getMessage());

        List<Map<String, String>> history = convertHistory(req.getHistory());

        return reactAgentService.reactStream(req.getMessage(), history, req.getUserId(), req.getLat(), req.getLng())
                .onErrorResume(e -> {
                    log.error("ReAct Agent 流式异常: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * 可用工具信息（调试用）
     */
    @GetMapping("/tools")
    public Result<Map<String, Object>> getTools() {
        Map<String, Object> tools = new HashMap<>();
        tools.put("semantic_search", "语义检索：在知识库、食谱库、问答库、社区中搜索相关内容。参数：query(String), topK(Integer, 可选)");
        tools.put("recipe_search", "食谱搜索：按关键词搜索具体食谱。参数：keyword(String), topN(Integer, 可选)");
        tools.put("knowledge_search", "知识搜索：按关键词搜索养生知识。参数：keyword(String), topN(Integer, 可选)");
        tools.put("qa_search", "问答搜索：搜索问答库中类似问题。参数：keyword(String), topN(Integer, 可选)");
        tools.put("user_profile", "用户画像：获取当前用户的体质、性别、健康目标等信息。无需参数。");
        tools.put("bmi_calculator", "BMI计算：根据身高(cm)和体重(kg)计算BMI指数。参数：height(Double), weight(Double)");
        tools.put("recipe_detail", "食谱详情：根据食谱ID获取完整做法。参数：recipeId(String)");
        tools.put("ingredient_lookup", "食材查询：查询食材的药性、功效、禁忌。参数：ingredient(String)");
        tools.put("food_compatibility", "食物搭配：检查两种食材能否一起吃。参数：food1(String), food2(String)");
        tools.put("weather_query", "天气查询：查询当前节气、季节和天气信息。参数：city(String, 可选)");
        return Result.success(tools);
    }

    // ==================== 辅助方法 ====================

    private List<Map<String, String>> convertHistory(List<MessageItem> historyItems) {
        if (historyItems == null) return List.of();
        return historyItems.stream().map(item -> {
            Map<String, String> m = new HashMap<>();
            m.put("role", item.getRole());
            m.put("content", item.getContent());
            return m;
        }).toList();
    }
}