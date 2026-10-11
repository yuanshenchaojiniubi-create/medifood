package com.medifood.recipe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 健康食谱服务启动类
 * 
 * @author MediFood Team
 */
@EnableFeignClients
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.medifood")
@MapperScan({"com.medifood.recipe.mapper", "com.medifood.recipe.notify"})
public class MediFoodRecipeApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MediFoodRecipeApplication.class, args);
    }
}

