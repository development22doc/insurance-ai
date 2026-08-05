package com.claimassist.platform.agent_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableKafka
public class AgentServiceApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        // Ensure config client disabled when local config server is not available
        System.setProperty("spring.cloud.config.enabled", "false");
        // Ensure local profile is active to use PostgreSQL datasource from application-local.yaml
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        if (activeProfiles.isBlank()) {
            System.setProperty("spring.profiles.active", "local");
        }
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
