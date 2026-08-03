package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.RefreshToken;
import com.claimassist.platform.customer_service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

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

        return refreshTokenRepository.save(rt);
    }

    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    public RefreshToken validateAndRotate(String token) {
        RefreshToken existing = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (existing.isRevoked()) {
            throw new IllegalArgumentException("Refresh token revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

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

        RefreshToken saved = refreshTokenRepository.save(rotated);

        existing.setRevoked(true);
        existing.setRotatedTo(saved.getToken());
        refreshTokenRepository.save(existing);

        return saved;
    }

    private String generateToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}

