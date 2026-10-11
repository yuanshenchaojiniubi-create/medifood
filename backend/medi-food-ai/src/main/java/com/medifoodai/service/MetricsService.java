package com.medifoodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medifoodai.mapper.AiFeedbackMapper;
import com.medifoodai.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 质量指标服务
 * <p>
 * 提供仪表盘数据：平均响应时间、RAG 命中率、反馈比率、Top 查询等
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class MetricsService {

    @Autowired
    private AiFeedbackMapper feedbackMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private TokenUsageService tokenUsageService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String RESPONSE_TIME_KEY = "ai:metrics:response_time";
    private static final String TOTAL_REQUESTS_KEY = "ai:metrics:total_requests";
    private static final String RAG_HIT_KEY = "ai:metrics:rag_hits";

    /**
     * 记录一次请求
     */
    public void recordRequest(boolean ragHit) {
        redisTemplate.opsForValue().increment(TOTAL_REQUESTS_KEY);
        if (ragHit) {
            redisTemplate.opsForValue().increment(RAG_HIT_KEY);
        }
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(long ms) {
        // 使用 Redis List 维护最近 100 次响应时间
        String key = RESPONSE_TIME_KEY;
        redisTemplate.opsForList().leftPush(key, String.valueOf(ms));
        redisTemplate.opsForList().trim(key, 0, 99);
    }

    /**
     * 获取仪表盘数据
     */
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        try {
            // 总请求数
            Object totalReq = redisTemplate.opsForValue().get(TOTAL_REQUESTS_KEY);
            long totalRequests = totalReq != null ? Long.parseLong(totalReq.toString()) : 0L;
            dashboard.put("totalRequests", totalRequests);

            // RAG 命中率
            Object ragHits = redisTemplate.opsForValue().get(RAG_HIT_KEY);
            long ragHitCount = ragHits != null ? Long.parseLong(ragHits.toString()) : 0L;
            double ragHitRate = totalRequests > 0 ? (double) ragHitCount / totalRequests * 100 : 0;
            dashboard.put("ragHitRate", String.format("%.1f%%", ragHitRate));

            // 平均响应时间
            List<Object> responseTimes = redisTemplate.opsForList().range(RESPONSE_TIME_KEY, 0, -1);
            if (responseTimes != null && !responseTimes.isEmpty()) {
                double avgMs = responseTimes.stream()
                        .mapToDouble(o -> Double.parseDouble(o.toString()))
                        .average()
                        .orElse(0);
                dashboard.put("avgResponseTimeMs", Math.round(avgMs));
            } else {
                dashboard.put("avgResponseTimeMs", 0);
            }

            // Token 用量
            dashboard.put("tokenToday", tokenUsageService.getTodayUsage());
            dashboard.put("tokenMonthly", tokenUsageService.getMonthlyUsage());
            dashboard.put("tokenTotal", tokenUsageService.getTotalUsage());

            // 反馈统计
            Map<String, Object> feedbackStats = getFeedbackStats();
            dashboard.put("feedback", feedbackStats);

        } catch (Exception e) {
            log.error("获取仪表盘数据失败: {}", e.getMessage());
            dashboard.put("error", "获取数据失败");
        }

        return dashboard;
    }

    /**
     * 获取反馈统计
     */
    private Map<String, Object> getFeedbackStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 总反馈数
            Long total = feedbackMapper.selectCount(null);
            stats.put("total", total);

            // 正面反馈数
            LambdaQueryWrapper<com.medifoodai.entity.AiFeedback> positiveWrapper = new LambdaQueryWrapper<>();
            positiveWrapper.eq(com.medifoodai.entity.AiFeedback::getFeedbackType, "positive");
            Long positive = feedbackMapper.selectCount(positiveWrapper);
            stats.put("positive", positive);

            // 负面反馈数
            LambdaQueryWrapper<com.medifoodai.entity.AiFeedback> negativeWrapper = new LambdaQueryWrapper<>();
            negativeWrapper.eq(com.medifoodai.entity.AiFeedback::getFeedbackType, "negative");
            Long negative = feedbackMapper.selectCount(negativeWrapper);
            stats.put("negative", negative);

            // 满意度
            double satisfaction = total > 0 ? (double) positive / total * 100 : 0;
            stats.put("satisfaction", String.format("%.1f%%", satisfaction));
        } catch (Exception e) {
            log.warn("获取反馈统计失败: {}", e.getMessage());
            stats.put("total", 0);
            stats.put("positive", 0);
            stats.put("negative", 0);
            stats.put("satisfaction", "0.0%");
        }

        return stats;
    }
}