package org.example.medifoodgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class},scanBasePackages = {"org.example.medifoodgateway", "com.medifood.common"})
public class MediFoodGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MediFoodGatewayApplication.class, args);
	}

}
