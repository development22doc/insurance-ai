package com.claimassist.platform.api_gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<String> publicRoutes) {
	public SecurityProperties(List<String> publicRoutes) {
		this.publicRoutes = publicRoutes == null ? java.util.List.of() : publicRoutes;
	}
}
