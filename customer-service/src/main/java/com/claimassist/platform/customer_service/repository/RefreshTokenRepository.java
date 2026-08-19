package com.claimassist.platform.customer_service.repository;

import com.claimassist.platform.customer_service.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    /**
     * Atomic compare-and-set (CAS) consumption of a refresh token for rotation.
     * This single conditional UPDATE is the authoritative gate that enforces the
     * "one refresh token may be consumed exactly once" invariant: it revokes the
     * token only when it is still valid (not already revoked and not expired).
     * <p>
     * Returns the affected-row count: 1 means this caller won the race (it is the
     * sole consumer), 0 means another caller already consumed/revoked it, or the
     * token is missing/expired. Callers inspect the return value and reject when 0.
     * <p>
     * The {@code idx_refresh_tokens_token} index supports the {@code token = ?}
     * predicate; it is intentionally NOT removed here (Phase 1 flagged it as
     * possibly redundant with the UNIQUE constraint pending runtime validation).
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.rotatedTo = :rotatedTo " +
            "WHERE rt.token = :token AND rt.revoked = false AND rt.expiresAt > :now")
    int consumeForRotation(@Param("token") String token,
                           @Param("rotatedTo") String rotatedTo,
                           @Param("now") Instant now);

}

