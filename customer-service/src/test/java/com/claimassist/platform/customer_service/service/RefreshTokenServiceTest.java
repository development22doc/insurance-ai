package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private PerformanceLogger performanceLogger;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Customer testCustomer;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .username("testuser@example.com")
                .fullName("Test User")
                .keycloakId("keycloak-123")
                .build();

        Instant now = Instant.now();
        testRefreshToken = RefreshToken.builder()
                .id(1L)
                .token("existing-token")
                .customer(testCustomer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1209600)) // 14 days
                .revoked(false)
                .build();
    }

    @Test
    void createRefreshToken_WithValidCustomer_ShouldCreateAndSaveToken() {
        // Given
        RefreshToken savedToken = RefreshToken.builder()
                .id(1L)
                .token("generated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(testCustomer);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotEmpty();
        assertThat(result.getCustomer()).isEqualTo(testCustomer);
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.getIssuedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(result.getIssuedAt());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(eventLogger).logDatabaseEvent(anyString(), anyString(), anyLong(), any());
        verify(performanceLogger).log(eq("REPOSITORY"), eq("repository.refresh-token.save"), anyLong(), any());
    }

    @Test
    void createRefreshToken_ShouldGenerateUniqueTokens() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> {
                    RefreshToken token = invocation.getArgument(0);
                    token.setId(1L);
                    return token;
                });

        // When
        RefreshToken token1 = refreshTokenService.createRefreshToken(testCustomer);
        RefreshToken token2 = refreshTokenService.createRefreshToken(testCustomer);

        // Then
        assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_ShouldSetCorrectExpirationTime() {
        // Given
        RefreshToken savedToken = RefreshToken.builder()
                .id(1L)
                .token("generated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(testCustomer);

        // Then
        assertThat(result.getIssuedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(result.getIssuedAt());
    }

    @Test
    void revoke_WithValidToken_ShouldRevokeToken() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);

        // When
        refreshTokenService.revoke("existing-token");

        // Then
        assertThat(testRefreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).findByToken("existing-token");
        verify(refreshTokenRepository).save(testRefreshToken);
        verify(eventLogger).logDatabaseEvent(anyString(), anyString(), anyLong(), any());
        verify(performanceLogger).log(eq("REPOSITORY"), eq("repository.refresh-token.revoke"), anyLong(), any());
    }

    @Test
    void revoke_WithNonExistentToken_ShouldDoNothing() {
        // Given
        when(refreshTokenRepository.findByToken("non-existent-token")).thenReturn(Optional.empty());

        // When
        refreshTokenService.revoke("non-existent-token");

        // Then
        verify(refreshTokenRepository).findByToken("non-existent-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(eventLogger, never()).logDatabaseEvent(anyString(), anyString(), anyLong(), any());
        verify(performanceLogger, never()).log(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void validateAndRotate_WithValidToken_ShouldRotateSuccessfully() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token("new-rotated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);

        // When
        RefreshToken result = refreshTokenService.validateAndRotate("existing-token");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotEqualTo("existing-token");
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.getCustomer()).isEqualTo(testCustomer);
        verify(refreshTokenRepository).findByToken("existing-token");
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        verify(eventLogger, times(2)).logDatabaseEvent(anyString(), anyString(), anyLong(), any());
        verify(eventLogger).logBusinessEvent(anyString(), anyString(), any());
        verify(performanceLogger, times(4)).log(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void validateAndRotate_ShouldMarkOldTokenAsRevoked() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token("new-rotated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getToken().equals("existing-token")) {
                // This is the old token being revoked
                return testRefreshToken;
            } else {
                // This is the new token being created
                return newToken;
            }
        });

        // When
        refreshTokenService.validateAndRotate("existing-token");

        // Then
        assertThat(testRefreshToken.isRevoked()).isTrue();
        assertThat(testRefreshToken.getRotatedTo()).isEqualTo("new-rotated-token");
    }

    @Test
    void validateAndRotate_WithNonExistentToken_ShouldThrowBadRequestException() {
        // Given
        when(refreshTokenRepository.findByToken("non-existent-token")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("non-existent-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).findByToken("non-existent-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void validateAndRotate_WithRevokedToken_ShouldThrowBadRequestException() {
        // Given
        testRefreshToken.setRevoked(true);
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("existing-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Refresh token revoked");

        verify(refreshTokenRepository).findByToken("existing-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void validateAndRotate_WithExpiredToken_ShouldThrowBadRequestException() {
        // Given
        Instant past = Instant.now().minusSeconds(3600); // 1 hour ago
        testRefreshToken.setIssuedAt(past.minusSeconds(1209600));
        testRefreshToken.setExpiresAt(past);
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("existing-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Refresh token expired");

        verify(refreshTokenRepository).findByToken("existing-token");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void validateAndRotate_ShouldGenerateNewTokenWithNewExpiration() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token("new-rotated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);

        // When
        RefreshToken result = refreshTokenService.validateAndRotate("existing-token");

        // Then
        assertThat(result.getToken()).isNotEqualTo("existing-token");
        assertThat(result.getIssuedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(result.getIssuedAt());
    }

    @Test
    void validateAndRotate_ShouldLogPerformanceMetrics() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token("new-rotated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);

        // When
        refreshTokenService.validateAndRotate("existing-token");

        // Then
        verify(performanceLogger).log(eq("BUSINESS"), eq("refresh.token.validation"), anyLong(), any());
        verify(performanceLogger).log(eq("REPOSITORY"), eq("repository.refresh-token.save"), anyLong(), any());
        verify(performanceLogger).log(eq("REPOSITORY"), eq("repository.refresh-token.revoke"), anyLong(), any());
        verify(performanceLogger).log(eq("BUSINESS"), eq("refresh.token.rotation"), anyLong(), any());
    }

    @Test
    void validateAndRotate_ShouldLogBusinessEvent() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token("new-rotated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);

        // When
        refreshTokenService.validateAndRotate("existing-token");

        // Then
        verify(eventLogger).logBusinessEvent(eq("customer-service"), eq("customer-service"), any());
    }

    @Test
    void createRefreshToken_ShouldLogDatabaseEvent() {
        // Given
        RefreshToken savedToken = RefreshToken.builder()
                .id(1L)
                .token("generated-token")
                .customer(testCustomer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1209600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        // When
        refreshTokenService.createRefreshToken(testCustomer);

        // Then
        verify(eventLogger).logDatabaseEvent(eq("customer-service"), eq("customer-service"), anyLong(), any());
    }

    @Test
    void revoke_ShouldLogDatabaseEvent() {
        // Given
        when(refreshTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(testRefreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);

        // When
        refreshTokenService.revoke("existing-token");

        // Then
        verify(eventLogger).logDatabaseEvent(eq("customer-service"), eq("customer-service"), anyLong(), any());
    }
}
