package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@SpringBootApplication
public class TempGatewayTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TempGatewayTestApplication.class, args);
    }

    @Component
    static class Runner implements CommandLineRunner {
        private final GatewayProperties gatewayProperties;

        public Runner(GatewayProperties gatewayProperties) {
            this.gatewayProperties = gatewayProperties;
        }

        @Override
        public void run(String... args) throws Exception {
            List<?> routes = gatewayProperties.getRoutes();
            System.out.println("GatewayProperties.PREFIX=" + GatewayProperties.PREFIX);
            System.out.println("Routes bound: " + (routes == null ? 0 : routes.size()));
            System.out.println("Routes detail: " + routes);
        }
    }
}

