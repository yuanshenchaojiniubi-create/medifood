package com.medifoodai.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 模块 Sentinel 限流熔断规则配置
 * <p>
 * AI 模块的特点：
 * 1. 调用外部 API（DeepSeek），有费用成本
 * 2. 单次请求耗时较长（流式响应）
 * 3. 需要防止恶意刷 API 导致费用失控
 * <p>
 * 限流策略：
 * - RAG 流式对话：10 QPS（控制 API 费用）
 * - 图片识别：5 QPS（视觉模型费用更高）
 * - 体检报告分析：3 QPS（耗时最长，资源消耗大）
 * - Agent 推理：5 QPS（多次工具调用，Token 消耗大）
 * <p>
 * 熔断策略：
 * - 慢调用比例 > 50%（响应时间 > 5秒），熔断 60 秒
 * - 异常比例 > 30%，熔断 30 秒
 *
 * @author MediFood Team
 */
@Slf4j
@Configuration
public class AiSentinelConfig {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
        log.info("✅ AI 模块 Sentinel 限流熔断规则初始化完成");
    }

    /**
     * AI 服务限流规则
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // RAG 流式对话：10 QPS
        rules.add(createFlowRule("ai_rag_stream", 10));
        // RAG 非流式对话：10 QPS
        rules.add(createFlowRule("ai_rag", 10));
        // 图片识别：5 QPS
        rules.add(createFlowRule("ai_image_analysis", 5));
        // 食物识别：5 QPS
        rules.add(createFlowRule("ai_food_analysis", 5));
        // 体检报告分析：3 QPS
        rules.add(createFlowRule("ai_report_analysis", 3));
        // Agent 推理：5 QPS
        rules.add(createFlowRule("ai_agent_react", 5));
        // 对话管理：20 QPS
        rules.add(createFlowRule("ai_conversation", 20));
        // 反馈接口：20 QPS
        rules.add(createFlowRule("ai_feedback", 20));

        FlowRuleManager.loadRules(rules);
        log.info("  📏 AI 限流规则: rag10/s, image5/s, report3/s, agent5/s, conv20/s");
    }

    /**
     * AI 服务熔断降级规则
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // RAG 流式对话：慢调用比例熔断
        // 当 50% 的请求响应时间超过 5 秒时，熔断 60 秒
        DegradeRule ragDegrade = new DegradeRule("ai_rag_stream")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(5.0)           // 慢调用阈值：5 秒
                .setTimeWindow(60)       // 熔断时长：60 秒
                .setMinRequestAmount(5)  // 最小请求数
                .setStatIntervalMs(10000) // 统计窗口：10 秒
                .setSlowRatioThreshold(0.5); // 慢调用比例：50%
        rules.add(ragDegrade);

        // 图片识别：异常比例熔断
        // 当 40% 的请求异常时，熔断 30 秒
        DegradeRule imageDegrade = new DegradeRule("ai_image_analysis")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.4)           // 异常比例：40%
                .setTimeWindow(30)       // 熔断时长：30 秒
                .setMinRequestAmount(3)  // 最小请求数
                .setStatIntervalMs(10000);
        rules.add(imageDegrade);

        // 体检报告分析：异常比例熔断
        DegradeRule reportDegrade = new DegradeRule("ai_report_analysis")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.4)
                .setTimeWindow(30)
                .setMinRequestAmount(3)
                .setStatIntervalMs(10000);
        rules.add(reportDegrade);

        DegradeRuleManager.loadRules(rules);
        log.info("  🔥 AI 熔断规则: rag慢调用50%熔断60s, image异常40%熔断30s, report异常40%熔断30s");
    }

    private FlowRule createFlowRule(String resource, double qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        return rule;
    }
}