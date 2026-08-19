package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LogoutServiceTest {

    private RestClient restClient;
    private KeycloakProperties props;
    private RefreshTokenService refreshTokenService;
    private RefreshTokenRepository refreshTokenRepository;
    private CustomerLookupService customerLookupService;
    private OAuth2LogoutService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        props = new KeycloakProperties(
                "http://keycloak:8180", "claimassist",
                "app-client", "secret", "http://redirect",
                "admin-client", "admin-secret");
        refreshTokenService = mock(RefreshTokenService.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        customerLookupService = mock(CustomerLookupService.class);
        service = new OAuth2LogoutService(restClient, props, refreshTokenService,
                refreshTokenRepository, customerLookupService);
    }

    @Test
    void logout_callsKeycloakEndpointAndRevokesLocalToken() {
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.empty());

        service.logout("rt-1");

        verify(restClient.post()).uri(props.logoutUri());
        verify(refreshTokenService).revoke("rt-1");
        verify(refreshTokenRepository).findByToken("rt-1");
    }

    @Test
    void logout_whenTokenFound_evictsCustomerCache() {
        Customer customer = Customer.builder().id(1L).username("alice@example.com").build();
        RefreshToken rt = RefreshToken.builder().token("rt-1").customer(customer).build();
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(rt));

        service.logout("rt-1");

        verify(customerLookupService).evictByUsername("alice@example.com");
    }

    @Test
    void logout_revokeFailure_isBestEffortAndDoesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(refreshTokenService).revoke("rt-1");

        service.logout("rt-1");

        verify(refreshTokenService).revoke("rt-1");
        verify(refreshTokenRepository, never()).findByToken(anyString());
    }
}