package com.claimassist.platform.policy_service;

import com.claimassist.platform.policy_service.migration.PolicyMigrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(PolicyMigrationProperties.class)
public class PolicyServiceApplication {
    public static void main(String[] args) {
        // Align with the project's established timezone initialization pattern
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        SpringApplication app = new SpringApplication(PolicyServiceApplication.class);
        app.addListeners(new FlywaySafetyProfileListener());
        app.run(args);
    }
}
