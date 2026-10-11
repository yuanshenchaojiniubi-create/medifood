package com.medifoodai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 智能助手服务启动类
 * <p>
 * 功能：RAG 智能问答、食物识别、报告分析、多轮对话、个性化推荐
 * </p>
 *
 * @author MediFood Team
 */
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(basePackages = "com.medifoodai.client")
@MapperScan("com.medifoodai.mapper")
@SpringBootApplication
@ComponentScan(
		basePackages = {"com.medifoodai", "com.medifood.common"}
)
public class MediFoodAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MediFoodAiApplication.class, args);
	}

}
