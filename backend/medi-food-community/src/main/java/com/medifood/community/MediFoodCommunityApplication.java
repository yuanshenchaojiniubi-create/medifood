package com.medifood.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.medifood.community","com.medifood.common"})
@MapperScan({"com.medifood.community.mapper", "com.medifood.community.audit"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.medifood.community.feign")
@EnableRabbit
@EnableScheduling
public class MediFoodCommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediFoodCommunityApplication.class, args);
    }
}

