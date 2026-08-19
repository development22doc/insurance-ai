package com.claimassist.platform.common_lib.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    @Test
    void allowedTransitions_perStateMachine() {
        assertThat(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.UNDER_REVIEW)).isTrue();
        assertThat(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.APPROVED)).isFalse();

        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.DOCS_REQUESTED)).isTrue();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.APPROVED)).isTrue();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.DENIED)).isTrue();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.PAID)).isFalse();

        assertThat(ClaimStatus.DOCS_REQUESTED.canTransitionTo(ClaimStatus.UNDER_REVIEW)).isTrue();

        assertThat(ClaimStatus.APPROVED.canTransitionTo(ClaimStatus.PAID)).isTrue();
        assertThat(ClaimStatus.APPROVED.canTransitionTo(ClaimStatus.DENIED)).isFalse();

        assertThat(ClaimStatus.DENIED.canTransitionTo(ClaimStatus.CLOSED)).isTrue();
        assertThat(ClaimStatus.DENIED.canTransitionTo(ClaimStatus.APPROVED)).isFalse();

        assertThat(ClaimStatus.PAID.canTransitionTo(ClaimStatus.CLOSED)).isTrue();

        assertThat(ClaimStatus.CLOSED.canTransitionTo(ClaimStatus.UNDER_REVIEW)).isFalse();
        assertThat(ClaimStatus.CLOSED.canTransitionTo(ClaimStatus.CLOSED)).isFalse();
    }

    @Test
    void allStatusesHaveAllValues() {
        assertThat(ClaimStatus.values())
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.DOCS_REQUESTED,
                        ClaimStatus.APPROVED, ClaimStatus.DENIED, ClaimStatus.PAID, ClaimStatus.CLOSED);
    }

    @Test
    void valueOfRoundTrip() {
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(ClaimStatus.valueOf(s.name())).isEqualTo(s);
        }
    }
}