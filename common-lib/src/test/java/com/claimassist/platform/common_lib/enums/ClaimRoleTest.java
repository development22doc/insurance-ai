package com.claimassist.platform.common_lib.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimRoleTest {

    @Test
    void policyholderPermissions() {
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.VIEW)).isTrue();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.SUBMIT_DOCUMENTS)).isTrue();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.VIEW_PARTIES)).isTrue();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.UPDATE_STATUS)).isFalse();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.MANAGE_PARTIES)).isFalse();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.VIEW_AUDIT_TRAIL)).isFalse();
    }

    @Test
    void adjusterPermissions() {
        assertThat(ClaimRole.ADJUSTER.hasPermission(ClaimPermission.VIEW)).isTrue();
        assertThat(ClaimRole.ADJUSTER.hasPermission(ClaimPermission.UPDATE_STATUS)).isTrue();
        assertThat(ClaimRole.ADJUSTER.hasPermission(ClaimPermission.MANAGE_PARTIES)).isTrue();
        assertThat(ClaimRole.ADJUSTER.hasPermission(ClaimPermission.VIEW_AUDIT_TRAIL)).isFalse();
    }

    @Test
    void auditorPermissions() {
        assertThat(ClaimRole.AUDITOR.hasPermission(ClaimPermission.VIEW)).isTrue();
        assertThat(ClaimRole.AUDITOR.hasPermission(ClaimPermission.VIEW_AUDIT_TRAIL)).isTrue();
        assertThat(ClaimRole.AUDITOR.hasPermission(ClaimPermission.UPDATE_STATUS)).isFalse();
        assertThat(ClaimRole.AUDITOR.hasPermission(ClaimPermission.MANAGE_PARTIES)).isFalse();
    }
}