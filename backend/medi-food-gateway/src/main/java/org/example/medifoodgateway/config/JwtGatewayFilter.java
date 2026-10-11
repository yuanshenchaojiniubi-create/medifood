package org.example.medifoodgateway.config;




import com.medifood.common.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter extends AbstractGatewayFilterFactory<JwtGatewayFilter.Config> {
    @Autowired
    private  JwtUtils jwtUtils;

    private final Environment env;

    public JwtGatewayFilter(JwtUtils jwtUtils, Environment env) {
        super(Config.class);
        this.jwtUtils = jwtUtils;
        this.env = env;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            HttpHeaders headers = request.getHeaders();

            // 1. 从请求头中获取 JWT Token
            String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "未提供认证 Token");
            }

            String token = authHeader.substring(7);

            // 2. 校验 JWT 是否合法
            if (!jwtUtils.isTokenValid(token)) {
                return unauthorized(exchange, "无效的 Token");
            }
           //3.判断token是否过期
            if (jwtUtils.isTokenExpired(token)) {
                return unauthorized(exchange, "Token 已过期");
            }

            // 4. 解析用户信息，例如 userId, username, roles
            Claims claims;
            try {
                claims = jwtUtils.parseToken(token); // 你需要实现这个方法，或使用 jjwt 解析
            } catch (Exception e) {
                return unauthorized(exchange, "Token 解析失败");
            }

            String userId = claims.getSubject(); // 假设 sub 是 userId
            String username = claims.get("username", String.class);

            // 5. 将用户信息写入请求头，透传给下游微服务
            //    注意：使用 set（替换语义）而非 header（追加语义），
            //    确保即使客户端伪造了同名头也不会残留多个值
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(h -> {
                        h.set("X-User-Id", userId);
                        h.set("X-Username", username != null ? username : "");
                        // 你可以继续添加 X-Roles, X-Permissions 等
                    })
                    .build();

            // 5. 继续过滤器链，请求将包含用户信息
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    // 配置类（可选，如果需要配置某些参数）
    public static class Config {
        // 可以放一些配置项，如是否启用、白名单等
    }
}
