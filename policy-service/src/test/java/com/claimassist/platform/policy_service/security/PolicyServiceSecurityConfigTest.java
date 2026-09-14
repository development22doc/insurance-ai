package com.claimassist.platform.policy_service.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import com.claimassist.platform.policy_service.config.PolicyServiceSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyServiceSecurityConfigTest.DummyController.class)
@Import({PolicyServiceSecurityConfig.class, PolicyServiceSecurityConfigTest.CorsTestConfig.class})
class PolicyServiceSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CorrelationIdFilter correlationIdFilter;

    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @TestConfiguration
    static class CorsTestConfig {
        @org.springframework.context.annotation.Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowedMethods(List.of("*"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return source;
        }
    }

    @RestController
    static class DummyController {
        @GetMapping("/dummy/resource")
        public String resource() {
            return "ok";
        }
    }

    @Test
    void jwtAuthenticatedGetOnProtectedEndpointIsAccepted() throws Exception {
        mockMvc.perform(get("/dummy/resource").with(jwt()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
