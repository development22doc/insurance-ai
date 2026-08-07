package com.claimassist.platform.common_lib.observability.event;

/**
 * Canonical event categories emitted by the platform-wide EventLogger.
 */
public enum EventType {
    REQUEST,
    BUSINESS,
    SECURITY,
    DATABASE,
    CACHE,
    KAFKA,
    PERFORMANCE,
    EXCEPTION
}

