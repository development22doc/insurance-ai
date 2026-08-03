package com.claimassist.platform.common_lib.event;

import lombok.Builder;

@Builder
public record ClaimUpdateResponseEvent(
        String sagaId,
        Long claimId,
        boolean success,
        String errorMessage
) {}
