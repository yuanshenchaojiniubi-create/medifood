package com.medifood.thirdparty.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * <p>
 * 提供 HTTP 调用能力，用于天气 API、逆地理编码等第三方服务调用。
 * 设置连接超时和读取超时，防止第三方服务不可用时阻塞过久。
 * </p>
 *
 * @author MediFood Team
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}