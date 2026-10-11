package com.medifood.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j (Swagger) API 文档配置
 * <p>
 * 访问地址：http://localhost:{port}/doc.html
 * 各微服务通过 springdoc.swagger-ui.title 区分模块名称
 * </p>
 *
 * @author MediFood Team
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("药食同源养生交流平台 API 文档")
                        .version("1.0.0")
                        .description("MediFood Circle 微服务 API 接口文档，支持在线调试")
                        .contact(new Contact()
                                .name("MediFood Team")
                                .email("admin@medifood.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}