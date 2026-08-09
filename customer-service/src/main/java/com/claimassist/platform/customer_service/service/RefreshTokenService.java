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

    public RefreshToken validateAndRotate(String token) {
        long start = System.currentTimeMillis();
        RefreshToken existing = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (existing.isRevoked()) {
            throw new BadRequestException("Refresh token revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Refresh token expired");
        }

        long validationDuration = System.currentTimeMillis() - start;
        performanceLogger.log("BUSINESS", "refresh.token.validation", validationDuration,
                Map.of("customerId", existing.getCustomer().getId()));

        // Rotation: create new token, mark old as revoked and link rotatedTo
        String newToken = generateToken();
        Instant now = Instant.now();
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

        existing.setRevoked(true);
        existing.setRotatedTo(saved.getToken());
        long revokeStart = System.currentTimeMillis();
        refreshTokenRepository.save(existing);
        long revokeDuration = System.currentTimeMillis() - revokeStart;
        Map<String,Object> revokeDetails = new java.util.HashMap<>();
        revokeDetails.put("event", "REFRESH_TOKEN_REVOKED");
        revokeDetails.put("customerId", existing.getCustomer().getId());
        revokeDetails.put("executionTimeMs", revokeDuration);
        eventLogger.logDatabaseEvent("customer-service", "customer-service", revokeDuration, revokeDetails);
        performanceLogger.log("REPOSITORY", "repository.refresh-token.revoke", revokeDuration,
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

