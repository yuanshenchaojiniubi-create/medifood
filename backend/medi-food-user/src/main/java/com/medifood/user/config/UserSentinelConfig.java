package com.medifood.user.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户模块 Sentinel 限流规则配置
 * <p>
 * 重点防护：
 * 1. 登录接口：严格限流，防止暴力破解
 * 2. 注册接口：防止批量注册垃圾账号
 * 3. 刷新 Token：适度限流
 *
 * @author MediFood Team
 */
@Slf4j
@Configuration
public class UserSentinelConfig {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        log.info("✅ 用户模块 Sentinel 限流规则初始化完成");
    }

    /**
     * 用户服务限流规则
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 登录接口：每秒 3 个请求（防止暴力破解密码）
        // 正常用户登录 1 秒内不会超过 3 次
        rules.add(createFlowRule("user_login", 3));

        // 注册接口：每秒 2 个请求（防止批量注册）
        rules.add(createFlowRule("user_register", 2));

        // Token 刷新：每秒 5 个请求
        rules.add(createFlowRule("user_refresh", 5));

        FlowRuleManager.loadRules(rules);
        log.info("  📏 用户模块限流规则: 登录3/s, 注册2/s, 刷新5/s");
    }

    private FlowRule createFlowRule(String resource, double qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        return rule;
    }
}