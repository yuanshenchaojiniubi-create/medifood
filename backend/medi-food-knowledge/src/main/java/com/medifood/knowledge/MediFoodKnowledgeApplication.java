package com.medifood.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 养生知识服务启动类
 * 
 * @author MediFood Team
 */
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.medifood")
@MapperScan({"com.medifood.knowledge.mapper", "com.medifood.knowledge.notify"})
public class MediFoodKnowledgeApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MediFoodKnowledgeApplication.class, args);
    }
}

