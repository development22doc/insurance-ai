package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OAuth2TokenServiceTest {

    private RestClient restClient;
    private MockRestServiceServer server;
    private CustomerRepository customerRepository;
    private RefreshTokenService refreshTokenService;
    private JwtDecoder jwtDecoder;
    private OAuth2TokenService service;
    private KeycloakProperties props;

    private static final String TOKEN_JSON =
            "{\"access_token\":\"at\",\"id_token\":\"idt\",\"refresh_token\":\"rt\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_expires_in\":1800,"
                    + "\"scope\":\"openid\"}";

    @BeforeEach
    void setUp() {
        props = new KeycloakProperties("http://keycloak:8180", "claimassist",
                "app-client", "secret", "http://redirect", "admin-client", "admin-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        customerRepository = mock(CustomerRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        jwtDecoder = mock(JwtDecoder.class);
        service = new OAuth2TokenService(restClient, props, customerRepository,
                refreshTokenService, jwtDecoder, mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    private void expectTokenCall() {
        server.expect(requestTo(props.tokenUri()))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(TOKEN_JSON));
    }

    private void stubValidIdToken(String username) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("preferred_username")).thenReturn(username);
        when(jwtDecoder.decode("idt")).thenReturn(jwt);
    }

    @Test
    void exchangeAuthorizationCode_success_createsLocalRefreshToken() {
        expectTokenCall();
        stubValidIdToken("alice@example.com");
        Customer customer = Customer.builder().id(1L).username("alice@example.com").fullName("Alice").build();
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.of(customer));

        AuthResponse res = service.exchangeAuthorizationCode("code", "verifier");

        assertThat(res.accessToken()).isEqualTo("at");
        assertThat(res.customerId()).isEqualTo(1L);
        assertThat(res.fullName()).isEqualTo("Alice");
        verify(refreshTokenService).createRefreshToken(any(), anyString(), any(), any());
        server.verify();
    }

    @Test
    void exchangeAuthorizationCode_noCustomer_foundFalse() {
        expectTokenCall();
        stubValidIdToken("ghost@example.com");
        when(customerRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());

        AuthResponse res = service.exchangeAuthorizationCode("code", "verifier");

        assertThat(res.customerId()).isNull();
        verify(refreshTokenService, never()).createRefreshToken(any(), any(), any(), any());
        server.verify();
    }

    @Test
    void exchangeAuthorizationCode_invalidIdToken_returnsNullCustomer() {
        expectTokenCall();
        when(jwtDecoder.decode("idt")).thenThrow(new JwtException("bad signature"));

        AuthResponse res = service.exchangeAuthorizationCode("code", "verifier");

        assertThat(res.customerId()).isNull();
        verify(customerRepository, never()).findByUsername(anyString());
        server.verify();
    }

    @Test
    void refreshToken_success_rotatesWhenNewTokenDiffers() {
        String json = "{\"access_token\":\"at\",\"id_token\":\"idt\",\"refresh_token\":\"new-token\","
                + "\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_expires_in\":0,\"scope\":\"openid\"}";
        server.expect(requestTo(props.tokenUri())).andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(json));
        stubValidIdToken("alice@example.com");
        Customer customer = Customer.builder().id(1L).username("alice@example.com").fullName("Alice").build();
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.of(customer));

        AuthResponse res = service.refreshToken("old-token");

        assertThat(res.accessToken()).isEqualTo("at");
        assertThat(res.refreshToken()).isEqualTo("new-token");
        verify(refreshTokenService).rotateIfPresent(anyString(), anyString(), any(), any());
        server.verify();
    }

    @Test
    void refreshToken_noNewToken_doesNotRotate() {
        expectTokenCall();
        stubValidIdToken("alice@example.com");
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());

        AuthResponse res = service.refreshToken("rt");

        assertThat(res.refreshToken()).isEqualTo("rt");
        verify(refreshTokenService, never()).rotateIfPresent(anyString(), anyString(), any(), any());
        server.verify();
    }
}