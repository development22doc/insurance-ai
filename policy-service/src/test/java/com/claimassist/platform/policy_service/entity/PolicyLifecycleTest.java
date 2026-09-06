package com.claimassist.platform.policy_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyLifecycleTest {

    @Test
    void pendingPayment_to_active_allowed() {
        Policy p = Policy.builder().status("PENDING_PAYMENT").build();
        p.transitionTo(LifecycleStatus.ACTIVE);
        assertThat(p.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void active_to_pendingPayment_not_allowed() {
        Policy p = Policy.builder().status("ACTIVE").build();
        assertThatThrownBy(() -> p.transitionTo(LifecycleStatus.PENDING_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelled_to_active_not_allowed_directly() {
        Policy p = Policy.builder().status("CANCELLED").build();
        assertThatThrownBy(() -> p.transitionTo(LifecycleStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void same_state_transition_is_noop() {
        Policy p = Policy.builder().status("ACTIVE").build();
        p.transitionTo(LifecycleStatus.ACTIVE);
        assertThat(p.getStatus()).isEqualTo("ACTIVE");
    }
}
