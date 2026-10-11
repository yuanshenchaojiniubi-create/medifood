package org.example.medifoodgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网关 CORS 配置。
 *
 * <p>允许的来源 = 配置项 {@code medifood.cors.allowed-origins}(逗号分隔,默认本地开发端口)
 * + 固定追加 {@code https://*.trycloudflare.com}(Cloudflare 随机隧道域名,前缀每次重启都会变,故用通配符)。
 * 使用 allowedOriginPatterns 支持通配符,并兼容 allowCredentials(true)(Spring 6 允许)。
 * 云服务器部署时通过环境变量 MEDIFOOD_CORS_ALLOWED_ORIGINS 覆盖为正式域名。
 */
@Configuration
public class CursConfig {

    @Value("${medifood.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        List<String> patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        // Cloudflare quick tunnel 随机子域名（地址每次重启都会变化，必须用通配符）
        patterns.add("https://*.trycloudflare.com");

        corsConfiguration.setAllowedOriginPatterns(patterns);
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsWebFilter(source);
    }
}
