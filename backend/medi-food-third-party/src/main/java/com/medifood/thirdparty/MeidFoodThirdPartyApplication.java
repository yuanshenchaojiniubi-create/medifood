package com.medifood.thirdparty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


/**
 * 第三方服务启动类
 * <p>
 * 提供天气查询、文件上传等第三方集成服务。
 * 不依赖数据库，排除 DataSource 自动配置。
 * </p>
 *
 * @author MediFood Team
 */
@EnableDiscoveryClient
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MeidFoodThirdPartyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeidFoodThirdPartyApplication.class, args);
    }

}