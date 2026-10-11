package com.medifood.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 * 
 * @author MediFood Team
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"com.medifood.user", "com.medifood.common"})
//@MapperScan("com.medifood.user.mapper")
public class MediFoodUserApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MediFoodUserApplication.class, args);
    }
}

