package org.example.medifoodgateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.*;

/**
 * Gateway 层 Sentinel 限流熔断配置
 * <p>
 * 功能：
 * 1. 全局 Sentinel 网关过滤器
 * 2. 自定义限流/熔断响应（友好 JSON 提示）
 * 3. 按 API 分组定义限流规则
 * 4. 熔断降级规则（慢调用比例、异常比例）
 *
 * @author MediFood Team
 */
@Slf4j
@Configuration
public class GatewaySentinelConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public GatewaySentinelConfig(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 全局 Sentinel 网关过滤器（最高优先级）
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    /**
     * 限流/熔断异常处理器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * 初始化限流规则和熔断规则
     */
    @PostConstruct
    public void initGatewayRules() {
        // 1. 初始化自定义限流响应
        initBlockHandler();

        // 2. 定义 API 分组（用于精细化限流）
        initApiDefinitions();

        // 3. 初始化网关限流规则
        initFlowRules();

        // 4. 初始化熔断降级规则
        initDegradeRules();

        log.info("✅ Sentinel 网关限流熔断规则初始化完成");
    }

    /**
     * 自定义限流/熔断时的响应内容
     * 返回友好的 JSON 格式提示，包含中文说明
     */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, exception) -> {
            String msg;
            String type = exception.getClass().getSimpleName();

            if (type.contains("Flow")) {
                msg = "请求过于频繁，请稍后再试";
            } else if (type.contains("Degrade")) {
                msg = "服务暂时不可用，正在恢复中，请稍后再试";
            } else if (type.contains("ParamFlow")) {
                msg = "操作过于频繁，请稍后再试";
            } else if (type.contains("SystemBlock")) {
                msg = "系统繁忙，请稍后再试";
            } else if (type.contains("Authority")) {
                msg = "权限不足，无法访问";
            } else {
                msg = "请求被限制，请稍后再试";
            }

            return ServerResponse
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(
                            String.format("{\"code\":429,\"message\":\"%s (Sentinel)\",\"data\":null}", msg)
                    ));
        });
    }

    /**
     * 定义 API 分组
     * 将不同的 API 路径分为不同的组，便于设置不同的限流策略
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 用户登录/注册（高频，需要严格限流）
        definitions.add(new ApiDefinition("user_auth")
                .setPredicateItems(new HashSet<>(Arrays.asList(
                        new ApiPathPredicateItem().setPattern("/api/user/login"),
                        new ApiPathPredicateItem().setPattern("/api/user/register"),
                        new ApiPathPredicateItem().setPattern("/api/user/refresh")
                ))));

        // AI 智能问答（资源消耗大，需要限流保护）
        definitions.add(new ApiDefinition("ai_service")
                .setPredicateItems(new HashSet<>(Arrays.asList(
                        new ApiPathPredicateItem().setPattern("/api/ai/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                ))));

        // 文件上传（带宽消耗大，需要限流）
        definitions.add(new ApiDefinition("upload_service")
                .setPredicateItems(new HashSet<>(Arrays.asList(
                        new ApiPathPredicateItem().setPattern("/api/third/upload/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX),
                        new ApiPathPredicateItem().setPattern("/api/message/upload/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                ))));

        // 通用读操作（查询类接口，限流较宽松）
        definitions.add(new ApiDefinition("read_api")
                .setPredicateItems(new HashSet<>(Arrays.asList(
                        new ApiPathPredicateItem().setPattern("/api/knowledge/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX),
                        new ApiPathPredicateItem().setPattern("/api/recipe/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX),
                        new ApiPathPredicateItem().setPattern("/api/community/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX),
                        new ApiPathPredicateItem().setPattern("/api/qa/**")
                                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX)
                ))));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
        log.info("  📋 API 分组定义完成: user_auth, ai_service, upload_service, read_api");
    }

    /**
     * 初始化网关限流规则
     * <p>
     * 限流策略说明：
     * - 登录/注册：每秒 5 个请求（防止暴力破解）
     * - AI 服务：每秒 10 个请求（防止 API 费用爆炸）
     * - 文件上传：每秒 3 个请求（防止带宽耗尽）
     * - 通用 API：每秒 100 个请求（正常业务）
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 用户认证接口：每秒 5 个请求
        rules.add(new GatewayFlowRule("user_auth")
                .setCount(5)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // AI 服务：每秒 10 个请求
        rules.add(new GatewayFlowRule("ai_service")
                .setCount(10)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 文件上传：每秒 3 个请求
        rules.add(new GatewayFlowRule("upload_service")
                .setCount(3)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        // 通用读 API：每秒 100 个请求
        rules.add(new GatewayFlowRule("read_api")
                .setCount(100)
                .setIntervalSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        GatewayRuleManager.loadRules(rules);
        log.info("  📏 限流规则加载完成: 认证5/s, AI 10/s, 上传3/s, 通用100/s");
    }

    /**
     * 初始化熔断降级规则
     * <p>
     * 熔断策略说明：
     * - AI 服务：使用【异常比例】熔断（非慢调用），因为 AI 流式对话本身就慢，10-30秒是正常的
     *   异常比例 > 50% 时熔断 30 秒（说明 AI API 可能挂了）
     * - 通用 API：异常比例 > 30%，熔断 20 秒
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // AI 服务熔断：异常比例 > 50% 时熔断
        // 注意：使用 ERROR_RATIO 而非 SLOW_REQUEST_RATIO，因为 AI 流式响应本就耗时较长
        DegradeRule aiDegrade = new DegradeRule("ai_service")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.5)          // 异常比例阈值：50%
                .setTimeWindow(30)      // 熔断时长：30 秒
                .setMinRequestAmount(5) // 最小请求数：5 个请求后才开始统计
                .setStatIntervalMs(20000); // 统计窗口：20 秒
        rules.add(aiDegrade);

        // 通用 API 熔断：异常比例 > 30% 时熔断
        DegradeRule apiDegrade = new DegradeRule("read_api")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.3)          // 异常比例阈值：30%
                .setTimeWindow(20)      // 熔断时长：20 秒
                .setMinRequestAmount(10) // 最小请求数：10
                .setStatIntervalMs(10000); // 统计窗口：10 秒
        rules.add(apiDegrade);

        DegradeRuleManager.loadRules(rules);
        log.info("  🔥 熔断规则加载完成: AI异常50%熔断30s, 通用异常30%熔断20s");
    }
}