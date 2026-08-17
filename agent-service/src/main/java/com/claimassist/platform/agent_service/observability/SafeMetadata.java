package com.claimassist.platform.agent_service.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PII / secret protection helpers for telemetry (Phase 6.13).
 * <p>
 * Observability must NEVER log raw claim ids, customer payloads, JWTs,
 * secrets or tool arguments. This small utility provides a deterministic,
 * non-reversible {@link #hash(String)} for sensitive identifiers (so an
 * engineer can correlate a tool event to a resource without exposing the raw
 * value) and {@link #redact(String)} to strip obvious secret/credential
 * patterns from free text before it is captured.
 */
public final class SafeMetadata {

    private SafeMetadata() {}

    /** SHA-256 (hex, first 16 chars) of an identifier - deterministic, non-reversible. */
    public static String hash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < bytes.length && sb.length() < 16; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed on the JVM; fall back to a fixed marker so
            // telemetry is never blocked by a hashing failure.
            return "[redacted]";
        }
    }

    /** Hash a numeric identifier (e.g. claimId) into a safe short hash. */
    public static String hash(Long value) {
        return value == null ? "" : hash(value.toString());
    }

    /**
     * Redact obvious credential patterns from free text. Does not guarantee
     * removal of arbitrary secrets - callers should avoid capturing raw
     * arguments in the first place (see tool telemetry, which captures only
     * safe metadata).
     */
    public static String redact(String text) {
        if (text == null) {
            return "";
        }
        String out = text
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "bearer [REDACTED]")
                .replaceAll("(?i)(password|secret|api[_-]?key|token)(\\s*[:=]\\s*)\\S+", "$1$2[REDACTED]");
        return out;
    }
}