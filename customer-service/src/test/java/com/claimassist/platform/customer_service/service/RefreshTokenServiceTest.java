package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Phase 2 refresh-token lifecycle tests: the locally stored token IS the Keycloak
 * token value, rotation is atomic (single-use), and revocation/expiry are enforced.
 */
class RefreshTokenServiceTest {

    private RefreshTokenRepository repo;
    private RefreshTokenService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repo, mock(EventLogger.class), mock(PerformanceLogger.class));
        setField(service, "refreshTtlSeconds", 1209600L);
        customer = Customer.builder().id(1L).username("alice").fullName("Alice").build();
    }

    @Test
    void createStoresTheActualKeycloakTokenValue() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        RefreshToken built = RefreshToken.builder().token("KEYCLOAK-RT").customer(customer)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).revoked(false).build();
        when(repo.save(any(RefreshToken.class))).thenReturn(built);

        RefreshToken saved = service.createRefreshToken(customer, "KEYCLOAK-RT", now, now.plusSeconds(300));

        assertThat(saved.getToken()).isEqualTo("KEYCLOAK-RT");
        assertThat(saved.getCustomer().getId()).isEqualTo(1L);
    }

    @Test
    void createRejectsBlankTokenSoNoDisconnectedTokenIsStored() {
        assertThatThrownBy(() -> service.createRefreshToken(customer, "  ", Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void revokeMarksTheMatchingTokenRevoked() {
        RefreshToken rt = RefreshToken.builder().id(9L).token("KEYCLOAK-RT")
                .customer(customer).revoked(false).build();
        when(repo.findByToken("KEYCLOAK-RT")).thenReturn(Optional.of(rt));
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke("KEYCLOAK-RT");

        assertThat(rt.isRevoked()).isTrue();
    }

    @Test
    void rotateIfPresentRotatesOldKeycloakTokenToNewKeycloakToken() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        RefreshToken old = RefreshToken.builder().id(1L).token("OLD-KEYCLOAK-RT")
                .customer(customer).revoked(false).expiresAt(now.plusSeconds(300)).build();
        when(repo.findByToken("OLD-KEYCLOAK-RT")).thenReturn(Optional.of(old));
        when(repo.consumeForRotation("OLD-KEYCLOAK-RT", "NEW-KEYCLOAK-RT", now)).thenReturn(1);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RefreshToken> rotated = service.rotateIfPresent(
                "OLD-KEYCLOAK-RT", "NEW-KEYCLOAK-RT", now, now.plusSeconds(600));

        assertThat(rotated).isPresent();
        assertThat(rotated.get().getToken()).isEqualTo("NEW-KEYCLOAK-RT");
        assertThat(rotated.get().getCustomer().getId()).isEqualTo(1L);
        verify(repo).consumeForRotation("OLD-KEYCLOAK-RT", "NEW-KEYCLOAK-RT", now);
    }

    @Test
    void rotateIfPresentRejectsRevokedLocalRecord() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        RefreshToken revoked = RefreshToken.builder().id(1L).token("USED")
                .customer(customer).revoked(true).build();
        when(repo.findByToken("USED")).thenReturn(Optional.of(revoked));
        when(repo.consumeForRotation(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotateIfPresent("USED", "NEW", now, now.plusSeconds(600)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void rotateIfPresentRejectsExpiredLocalRecord() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        RefreshToken expired = RefreshToken.builder().id(1L).token("EXPIRED")
                .customer(customer).revoked(false).expiresAt(now.minusSeconds(1)).build();
        when(repo.findByToken("EXPIRED")).thenReturn(Optional.of(expired));
        when(repo.consumeForRotation(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotateIfPresent("EXPIRED", "NEW", now, now.plusSeconds(600)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rotateIfPresentIsEmptyWhenNoLocalRecord() {
        when(repo.findByToken("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.rotateIfPresent("UNKNOWN", "NEW", Instant.now(), null)).isEmpty();
        verify(repo, never()).consumeForRotation(any(), any(), any());
    }

    @Test
    void rotateIfPresentIsEmptyWhenKeycloakDoesNotRotate() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        // Same token back from Keycloak = no rotation; never consume locally.
        assertThat(service.rotateIfPresent("SAME", "SAME", now, now.plusSeconds(300))).isEmpty();
        verify(repo, never()).findByToken(any());
        verify(repo, never()).consumeForRotation(any(), any(), any());
    }
}