package com.medifood.qa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.medifood.qa", "com.medifood.common"})
@MapperScan({"com.medifood.qa.mapper", "com.medifood.qa.notify"})
@EnableFeignClients
public class MediFoodQaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediFoodQaApplication.class, args);
    }
}

