package com.claimassist.platform.customer_service.security;

import com.claimassist.platform.common_lib.observability.CorrelationIdFilter;
import com.claimassist.platform.common_lib.security.KeycloakJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-chain tests for CustomerSecurityConfig. These drive the real
 * SecurityFilterChain in isolation (plus a dummy controller) to verify the
 * stateless JWT-bearer model: state-changing requests authenticated only by a
 * bearer JWT (with no CSRF token) are accepted, because CSRF protection relies
 * on ambient browser credentials that this cookie-less, non-browser-reachable
 * resource server never uses.
 */
@WebMvcTest(CustomerSecurityConfigTest.DummyController.class)
@Import({CustomerSecurityConfig.class, CustomerSecurityConfigTest.CorsTestConfig.class})
class CustomerSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CorrelationIdFilter correlationIdFilter;
    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    @MockBean
    private com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger;
    @MockBean
    private com.claimassist.platform.common_lib.security.CurrentUserProvider currentUserProvider;

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
        @PostMapping("/dummy/state")
        public String state() { return "state"; }

        @GetMapping("/dummy/resource")
        public String resource() { return "ok"; }
    }

    @Test
    void jwtAuthenticatedStateChangingPostWithoutCsrfTokenIsAccepted() throws Exception {
        // CSRF is disabled for this stateless JWT-bearer resource server (no
        // cookie/session-based authentication, not browser-reachable), so a
        // state-changing request authenticated only by a bearer JWT must be
        // accepted even though it carries no CSRF token.
        mockMvc.perform(post("/dummy/state")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void jwtAuthenticatedGetOnProtectedEndpointIsAccepted() throws Exception {
        // A JWT-authenticated GET on a protected endpoint is served (no CSRF
        // required for read + authentication enforced by the bearer resource server).
        mockMvc.perform(get("/dummy/resource").with(jwt()))
                .andExpect(status().isOk());
    }
}