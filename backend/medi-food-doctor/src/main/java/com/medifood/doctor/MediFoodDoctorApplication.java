package com.medifood.doctor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 医师服务启动类
 *
 * @author MediFood Team
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.medifood")
@MapperScan("com.medifood.doctor.mapper")
public class MediFoodDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediFoodDoctorApplication.class, args);
    }
}