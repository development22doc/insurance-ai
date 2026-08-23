package com.claimassist.platform.claims_service.controller;

import com.claimassist.platform.claims_service.dto.claim.ClaimRequest;
import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.dto.claim.UpdateClaimStatusRequest;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.query.ClaimQueryService;
import com.claimassist.platform.claims_service.service.query.impl.ClaimQueryServiceImpl;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4, Section 10 (IDOR): the public claim endpoints must be gated by
 * method-level {@code @PreAuthorize} expressions (enforced by the OAuth2
 * resource server + method security in the full app context; here, on a
 * {@code @WebMvcTest} slice with security permitted, we assert the annotations
 * are correctly wired on every claim-scoped endpoint) and by the service-layer
 * ownership rules verified in the query/command service tests.
 */
@WebMvcTest(ClaimController.class)
@ActiveProfiles("test")
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClaimCommandService claimCommandService;

    @MockBean
    private ClaimQueryService claimQueryService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void getClaimByIdIsGatedByCanView() throws Exception {
        // The @PreAuthorize for claim lookup lives on the shared service method
        // (enforced for every entry point), not on the thin controller delegate.
        Method method = ClaimQueryServiceImpl.class.getMethod("getClaimById", Long.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@security.canView(#claimId)");
    }

    @Test
    void updateStatusIsGatedByCanUpdateStatus() throws Exception {
        Method method = ClaimController.class.getMethod("updateStatus", Long.class, UpdateClaimStatusRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@security.canUpdateStatus(#id)");
    }

    @Test
    void getMyClaimsReturns200() throws Exception {
        when(claimQueryService.getMyClaims()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/claims"))
                .andExpect(status().isOk());
    }

    @Test
    void submitClaimRejectsValidationViolations() throws Exception {
        ClaimRequest invalid = new ClaimRequest(null, "", Instant.now().plusSeconds(3600), null);
        mockMvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitClaimReturnsCreated() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(claimCommandService.submitClaim(any()))
                .thenReturn(new ClaimResponse(1L, "CLM-1", "SUBMITTED", "FIRE"));
        ClaimRequest valid = new ClaimRequest(5L, "FIRE", Instant.now().minusSeconds(60), 1000L);
        mockMvc.perform(post("/claims")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isCreated());
    }

    @Test
    void getClaimByIdDelegatesToQueryService() throws Exception {
        ClaimSummaryResponse response = new ClaimSummaryResponse(1L, "CLM-1", 5L, "FIRE",
                "SUBMITTED", 1000L, null, "POLICYHOLDER", Instant.now(), Instant.now());
        when(claimQueryService.getClaimById(1L)).thenReturn(response);
        mockMvc.perform(get("/claims/1"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class PermitAllSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf
                    .csrfTokenRepository(
                            org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()
                    )
                    .ignoringRequestMatchers(
                            "/actuator/**",
                            "/webhooks/**",
                            "/claims/**"
                    )
            )
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}