package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.RefreshTokenRepository;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Persists and rotates the Keycloak-issued refresh token lifecycle.
 *
 * <p>Phase 2 fixed a critical disconnect: previously a <em>locally generated</em>
 * random token was stored, while the client kept the Keycloak token, so local
 * validation/rotation/revocation could never match the presented token and
 * {@code /auth/refresh} always failed. Keycloak is authoritative for the OAuth2
 * lifecycle, so this service now persists and rotates the <b>actual Keycloak
 * refresh token value</b>.
 *
 * <p>Rotation is enforced by the atomic compare-and-set (CAS) in
 * {@link RefreshTokenRepository#consumeForRotation}: a single conditional UPDATE
 * revokes the old token only if still valid, so a given refresh token can be
 * consumed exactly once (replay protection / concurrent-refresh guard). The
 * CAS and creation of the rotated token run in ONE transaction; no external
 * (Keycloak) call happens inside it - the caller performs the Keycloak exchange
 * first, then invokes this service. Refresh tokens are never logged.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    @Value("${security.refresh-token.ttl-seconds:1209600}") // 14 days default
    private long refreshTtlSeconds;

    /**
     * Persists a Keycloak-issued refresh token (the exact value the client will
     * present on refresh/logout). A null/blank value is rejected rather than
     * silently stored, so a disconnected local token can never be created.
     */
    public RefreshToken createRefreshToken(Customer customer, String tokenValue,
                                           Instant issuedAt, Instant expiresAt) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new IllegalArgumentException("Cannot persist a blank Keycloak refresh token");
        }
        Instant now = issuedAt != null ? issuedAt : Instant.now();
        Instant exp = expiresAt != null ? expiresAt : now.plusSeconds(refreshTtlSeconds);
        RefreshToken rt = RefreshToken.builder()
                .token(tokenValue)
                .customer(customer)
                .issuedAt(now)
                .expiresAt(exp)
                .revoked(false)
                .build();

        long start = System.currentTimeMillis();
        RefreshToken saved = refreshTokenRepository.save(rt);
        long duration = System.currentTimeMillis() - start;
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("event", "REFRESH_TOKEN_SAVED");
        details.put("customerId", customer.getId());
        details.put("executionTimeMs", duration);
        eventLogger.logDatabaseEvent("customer-service", "customer-service", duration, details);
        performanceLogger.log("REPOSITORY", "repository.refresh-token.save", duration,
                Map.of("customerId", customer.getId()));
        return saved;
    }

    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            long start = System.currentTimeMillis();
            refreshTokenRepository.save(rt);
            long duration = System.currentTimeMillis() - start;
            Map<String, Object> details = new java.util.HashMap<>();
            details.put("event", "REFRESH_TOKEN_REVOKED");
            details.put("customerId", rt.getCustomer().getId());
            details.put("executionTimeMs", duration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", duration, details);
            performanceLogger.log("REPOSITORY", "repository.refresh-token.revoke", duration,
                    Map.of("customerId", rt.getCustomer().getId()));
        });
    }

    /**
     * Atomically rotates the <b>old</b> Keycloak refresh token to the <b>new</b>
     * Keycloak refresh token Keycloak issued, using the single conditional UPDATE
     * as the authoritative concurrency gate.
     *
     * <p>Call AFTER the Keycloak exchange (never inside the DB transaction). The
     * returned Optional is empty when there is no local record for the token
     * (e.g. a token minted before persistence was added) - Keycloak remains
     * authoritative in that case and the refresh is allowed. When a local record
     * <em>does</em> exist but is already revoked/expired/used, this throws, which
     * is the local single-use/expiry guard on top of Keycloak's own rotation.
     */
    @Transactional
    public Optional<RefreshToken> rotateIfPresent(String oldToken, String newToken,
                                                  Instant now, Instant newExpiresAt) {
        if (newToken == null || newToken.isBlank() || newToken.equals(oldToken)) {
            return Optional.empty();
        }
        long start = System.currentTimeMillis();

        RefreshToken existing = refreshTokenRepository.findByToken(oldToken).orElse(null);
        if (existing == null) {
            // No local record - Keycloak is authoritative; nothing to rotate locally.
            return Optional.empty();
        }

        int consumed = refreshTokenRepository.consumeForRotation(oldToken, newToken, now);
        if (consumed == 0) {
            if (existing.isRevoked()) {
                throw new BadRequestException("Refresh token revoked");
            }
            throw new BadRequestException("Refresh token expired");
        }

        Instant exp = newExpiresAt != null ? newExpiresAt : now.plusSeconds(refreshTtlSeconds);
        RefreshToken rotated = RefreshToken.builder()
                .token(newToken)
                .customer(existing.getCustomer())
                .issuedAt(now)
                .expiresAt(exp)
                .revoked(false)
                .build();

        long saveStart = System.currentTimeMillis();
        RefreshToken saved = refreshTokenRepository.save(rotated);
        long saveDuration = System.currentTimeMillis() - saveStart;
        Map<String, Object> saveDetails = new java.util.HashMap<>();
        saveDetails.put("event", "REFRESH_TOKEN_SAVED");
        saveDetails.put("customerId", existing.getCustomer().getId());
        saveDetails.put("executionTimeMs", saveDuration);
        eventLogger.logDatabaseEvent("customer-service", "customer-service", saveDuration, saveDetails);
        performanceLogger.log("REPOSITORY", "repository.refresh-token.save", saveDuration,
                Map.of("customerId", existing.getCustomer().getId()));

        long totalDuration = System.currentTimeMillis() - start;
        Map<String, Object> rotatedDetails = new java.util.HashMap<>();
        rotatedDetails.put("event", "TOKEN_ROTATED");
        rotatedDetails.put("customerId", existing.getCustomer().getId());
        rotatedDetails.put("executionTimeMs", totalDuration);
        rotatedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
        eventLogger.logBusinessEvent("customer-service", "customer-service", rotatedDetails);
        performanceLogger.log("BUSINESS", "refresh.token.rotation", totalDuration,
                Map.of("customerId", existing.getCustomer().getId()));

        return Optional.of(saved);
    }

}