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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.refresh-token.ttl-seconds:1209600}") // 14 days default
    private long refreshTtlSeconds;

    public RefreshToken createRefreshToken(Customer customer) {
        String token = generateToken();
        Instant now = Instant.now();
        RefreshToken rt = RefreshToken.builder()
                .token(token)
                .customer(customer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(refreshTtlSeconds))
                .revoked(false)
                .build();

        long start = System.currentTimeMillis();
        RefreshToken saved = refreshTokenRepository.save(rt);
        long duration = System.currentTimeMillis() - start;
        Map<String,Object> details = new java.util.HashMap<>();
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
            Map<String,Object> details = new java.util.HashMap<>();
            details.put("event","REFRESH_TOKEN_REVOKED");
            details.put("customerId", rt.getCustomer().getId());
            details.put("executionTimeMs", duration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", duration, details);
            performanceLogger.log("REPOSITORY","repository.refresh-token.revoke",duration,
                    Map.of("customerId",rt.getCustomer().getId()));
        });
    }

    /**
     * Validates and rotates a refresh token using an atomic compare-and-set (CAS).
     *
     * <p>The single conditional UPDATE ({@link RefreshTokenRepository#consumeForRotation})
     * is the authoritative concurrency gate: it revokes the token only if it is still
     * valid (not revoked, not expired). Exactly one concurrent request can affect 1 row;
     * every other concurrent request affects 0 rows and is rejected below. This closes the
     * double-rotation race where two requests could both pass a read-then-check and both
     * rotate the same token.
     *
     * <p>The CAS + creation of the rotated token run in ONE transaction, so if creating the
     * new token fails the whole transaction rolls back (the old token is not left revoked
     * with no replacement). No pessimistic lock is held and no external network call happens
     * inside this transaction.
     */
    @Transactional
    public RefreshToken validateAndRotate(String token) {
        long start = System.currentTimeMillis();
        Instant now = Instant.now();
        String newToken = generateToken();

        int consumed = refreshTokenRepository.consumeForRotation(token, newToken, now);
        if (consumed == 0) {
            // The atomic gate rejected the token (missing, expired, revoked, or already
            // consumed by a concurrent request). Re-read ONLY to pick a consistent,
            // non-leaky error message - the CAS above is the real guard, this read is not
            // used to decide success.
            RefreshToken existing = refreshTokenRepository.findByToken(token).orElse(null);
            if (existing == null) {
                throw new BadRequestException("Invalid refresh token");
            }
            if (existing.isRevoked()) {
                throw new BadRequestException("Refresh token revoked");
            }
            throw new BadRequestException("Refresh token expired");
        }

        // This request won the CAS - it is the sole consumer. Load to build the rotated token.
        RefreshToken existing = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        long validationDuration = System.currentTimeMillis() - start;
        performanceLogger.log("BUSINESS", "refresh.token.validation", validationDuration,
                Map.of("customerId", existing.getCustomer().getId()));

        RefreshToken rotated = RefreshToken.builder()
                .token(newToken)
                .customer(existing.getCustomer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(refreshTtlSeconds))
                .revoked(false)
                .build();

        long saveStart = System.currentTimeMillis();
        RefreshToken saved = refreshTokenRepository.save(rotated);
        long saveDuration = System.currentTimeMillis() - saveStart;
        Map<String,Object> saveDetails = new java.util.HashMap<>();
        saveDetails.put("event", "REFRESH_TOKEN_SAVED");
        saveDetails.put("customerId", existing.getCustomer().getId());
        saveDetails.put("executionTimeMs", saveDuration);
        eventLogger.logDatabaseEvent("customer-service", "customer-service", saveDuration, saveDetails);
        performanceLogger.log("REPOSITORY", "repository.refresh-token.save", saveDuration,
                Map.of("customerId", existing.getCustomer().getId()));

         long totalDuration = System.currentTimeMillis() - start;
         Map<String,Object> rotatedDetails = new java.util.HashMap<>();
         rotatedDetails.put("event","TOKEN_ROTATED");
         rotatedDetails.put("customerId", existing.getCustomer().getId());
         rotatedDetails.put("executionTimeMs", totalDuration);
         rotatedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
         eventLogger.logBusinessEvent("customer-service","customer-service",rotatedDetails);
        performanceLogger.log("BUSINESS","refresh.token.rotation", totalDuration, Map.of("customerId", existing.getCustomer().getId()));

        return saved;
    }


    private String generateToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}

