package com.claimassist.platform.agent_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunable cache settings for the agent's read operations.
 * <p>
 * The cache is a pure performance optimization - the database / backend
 * services remain the source of truth. Every read cached here is a
 * resource-scoped (claim/policy), read-only DTO; authorization is ALWAYS
 * enforced by the tool layer (fail-closed {@code checkPermission}) BEFORE the
 * cache is consulted, so a cache hit can never bypass authorization.
 * <p>
 * All values are externalized via {@code agent.cache.*} (env:
 * {@code AGENT_CACHE_*}). TTLs are deliberately per-data-class, not a single
 * global value: each reflects that data's freshness, volatility, and backend
 * cost.
 */
@Data
@ConfigurationProperties(prefix = "agent.cache")
public class CacheProperties {

    /** Master kill-switch. When false, all cache reads/writes are skipped. */
    private boolean enabled = true;

    /**
     * Schema/format version embedded in every cache key (e.g. {@code v1}), so a
     * future serialized-DTO change can never silently read stale, incompatible
     * entries - bumping it creates a new key namespace instead.
     */
    private String version = "v1";

    /**
     * TTL for claim-status reads. Status is relatively volatile (changes on
     * proposal/approval) and correctness-sensitive, so keep it short.
     */
    private Duration claimStatusTtl = Duration.ofSeconds(30);

    /**
     * TTL for policy-coverage reads. Coverage is stable (rarely changes), so a
     * longer TTL is safe and maximises hit rate for the most expensive backend
     * call.
     */
    private Duration policyCoverageTtl = Duration.ofMinutes(5);

    /**
     * TTL for claim-documents metadata reads. Document metadata is stable-ish but
     * can change when documents are uploaded, so a moderate TTL is used.
     */
    private Duration claimDocumentsTtl = Duration.ofMinutes(2);
}