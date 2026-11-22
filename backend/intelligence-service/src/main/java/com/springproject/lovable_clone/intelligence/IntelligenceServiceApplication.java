package com.springproject.lovable_clone.intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
public class IntelligenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligenceServiceApplication.class, args);
    }
}
