package org.example.medifoodgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.Set;

/**
 * 网关统一路由定义（唯一的路由来源）。
 *
 * <p>说明：
 * <ul>
 *   <li>各服务目标地址通过 {@code medifood.service.<name>} 配置注入，默认指向本机端口（本地联调）。
 *       云服务器部署时用环境变量覆盖即可，例如 {@code MEDIFOOD_SERVICE_USER=http://user-service:8081}。</li>
 *   <li>业务路由统一挂 JWT 鉴权（白名单精确匹配除外）；AI 与第三方（OSS 签名/上传）也已纳入鉴权，
 *       防止匿名刷 AI 接口与滥用云存储。</li>
 *   <li>所有路由入口都会剥离客户端自带的 X-User-Id / X-Username 头，
 *       身份头只能由网关在 JWT 校验通过后写入，防止伪造身份绕过鉴权。</li>
 *   <li>路径重写必须与各服务 Controller 的 @RequestMapping 前缀保持一致，勿随意修改。</li>
 * </ul>
 */
@Configuration
public class GatewayOfJwtConfig {

    /**
     * 用户服务公开路径白名单：必须精确匹配。
     * <p>严禁使用 contains 子串匹配 —— 攻击者可构造 /api/user/login/../info
     * 之类路径骗过白名单后经下游路径标准化绕过鉴权。</p>
     * <p>注：/api/user/internal/** 不再放行。内部接口仅供微服务间 Feign 直连
     * （走 Nacos 服务名，不经网关），公网不应可达。</p>
     */
    private static final Set<String> PUBLIC_USER_PATHS = Set.of(
            "/api/user/login",
            "/api/user/register",
            "/api/user/refresh"
    );

    /**
     * 剥离客户端自带的身份透传头。
     * <p>X-User-Id / X-Username 属于网关与下游服务之间的内部信任凭证，
     * 只允许网关在 JWT 校验通过后写入。客户端自带的同名头一律删除，
     * 否则匿名/绕过路径上的下游接口会误信请求头中的伪造身份。</p>
     */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Value("${medifood.service.user:http://localhost:8081}")
    private String userUri;

    @Value("${medifood.service.third-party:http://localhost:1195}")
    private String thirdPartyUri;

    @Value("${medifood.service.ai:http://localhost:3545}")
    private String aiUri;

    @Value("${medifood.service.knowledge:http://localhost:8082}")
    private String knowledgeUri;

    @Value("${medifood.service.recipe:http://localhost:8083}")
    private String recipeUri;

    @Value("${medifood.service.community:http://localhost:8085}")
    private String communityUri;

    @Value("${medifood.service.qa:http://localhost:8086}")
    private String qaUri;

    @Value("${medifood.service.message:http://localhost:8088}")
    private String messageUri;

    @Value("${medifood.service.doctor:http://localhost:8209}")
    private String doctorUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, JwtGatewayFilter jwtGatewayFilter) {
        return builder.routes()

                // ========== 1. 用户服务（Controller 前缀 /api/user）==========
                .route("user_route", r -> r.path("/api/user/**")
                        .filters(f -> f.rewritePath("/api/user/(?<segment>.*)", "/api/user/${segment}")
                                .filter((exchange, chain) -> {
                                    // 先剥离客户端伪造的身份头（无论后续是否走鉴权）
                                    exchange = stripIdentityHeaders(exchange);
                                    String path = exchange.getRequest().getPath().value();
                                    // 公开路径精确匹配（登录/注册/刷新Token）；
                                    // internal 接口仅供微服务 Feign 直连，不再从网关放行
                                    if (PUBLIC_USER_PATHS.contains(path)) {
                                        return chain.filter(exchange); // 不走 JWT 过滤
                                    }
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(userUri))

                // ========== 1b. 管理端（用户服务）==========
                .route("admin_route", r -> r.path("/api/admin/**")
                        .filters(f -> f.filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(userUri))

                // ========== 2. 第三方服务（OSS 签名/上传；天气由业务服务内部 Feign 调用，不经网关）==========
                // OSS 签名与上传必须登录后调用，防止匿名滥用云存储刷文件
                .route("third_party_route", r -> r.path("/api/third/**")
                        .filters(f -> f.rewritePath("/api/third/(?<segment>.*)", "/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(thirdPartyUri))

                // ========== 3. AI 服务（Controller 前缀 /ai、/ai/agent）==========
                // AI 接口必须登录后调用，防止匿名刷 DeepSeek/SiliconFlow 付费额度
                .route("ai_route", r -> r.path("/api/ai/**")
                        .filters(f -> f.rewritePath("/api/ai/(?<segment>.*)", "/ai/${segment}")
                                .setResponseHeader("Content-Type", "text/event-stream")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(aiUri))

                // ========== 4. 养生知识（Controller 前缀 /knowledge）==========
                .route("knowledge_route", r -> r.path("/api/knowledge/**")
                        .filters(f -> f.rewritePath("/api/knowledge/(?<segment>.*)", "/knowledge/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    String path = exchange.getRequest().getPath().value();
                                    String method = exchange.getRequest().getMethod().name();
                                    if (GatewayPublicPaths.allowsAnonymous(path, method)) {
                                        return chain.filter(exchange);
                                    }
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(knowledgeUri))

                // ========== 5. 健康食谱（Controller 前缀 /api/recipe）==========
                .route("recipe_route", r -> r.path("/api/recipe/**")
                        .filters(f -> f.rewritePath("/api/recipe/(?<segment>.*)", "/api/recipe/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    String path = exchange.getRequest().getPath().value();
                                    String method = exchange.getRequest().getMethod().name();
                                    if (GatewayPublicPaths.allowsAnonymous(path, method)) {
                                        return chain.filter(exchange);
                                    }
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(recipeUri))

                // ========== 6. 社区服务（Controller 前缀 /api/community）==========
                .route("community_route", r -> r.path("/api/community/**")
                        .filters(f -> f.rewritePath("/api/community/(?<segment>.*)", "/api/community/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(communityUri))

                // ========== 7. 问答服务（Controller 前缀 /api/qa）==========
                .route("qa_route", r -> r.path("/api/qa/**")
                        .filters(f -> f.rewritePath("/api/qa/(?<segment>.*)", "/api/qa/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(qaUri))

                // ========== 8. 消息服务 REST（Controller 前缀 /api/message）==========
                .route("message_route", r -> r.path("/api/message/**")
                        .filters(f -> f.rewritePath("/api/message/(?<segment>.*)", "/api/message/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(messageUri))

                // ========== 9. 医生服务（Controller 前缀 /api/doctor）==========
                .route("doctor_route", r -> r.path("/api/doctor/**")
                        .filters(f -> f.rewritePath("/api/doctor/(?<segment>.*)", "/api/doctor/${segment}")
                                .filter((exchange, chain) -> {
                                    exchange = stripIdentityHeaders(exchange);
                                    return jwtGatewayFilter.apply(new JwtGatewayFilter.Config()).filter(exchange, chain);
                                }))
                        .uri(doctorUri))

                // ========== 10. 消息服务 WebSocket（握手不验 JWT，消息服务内部校验 Token）==========
                .route("ws_message_route", r -> r.path("/ws/**")
                        .uri(messageUri))

                .build();
    }
}
