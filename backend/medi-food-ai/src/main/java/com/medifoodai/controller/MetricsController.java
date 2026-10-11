package com.medifoodai.controller;

import com.medifood.common.result.Result;
import com.medifoodai.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 指标控制器
 * <p>
 * 提供 AI 服务质量仪表盘数据（管理员用）
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/ai/metrics")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    /**
     * 获取仪表盘数据
     * <p>
     * 返回：总请求数、RAG 命中率、平均响应时间、Token 用量、反馈统计
     * </p>
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = metricsService.getDashboard();
            return Result.success(dashboard);
        } catch (Exception e) {
            log.error("获取仪表盘数据失败: {}", e.getMessage());
            return Result.fail("获取仪表盘数据失败: " + e.getMessage());
        }
    }
}