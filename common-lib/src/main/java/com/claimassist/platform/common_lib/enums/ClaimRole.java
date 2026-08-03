package com.claimassist.platform.common_lib.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who this user is, with respect to ONE specific claim. Deliberately not the
 * same set as a generic "user role" - AUDITOR in particular has no equivalent
 * in a typical SaaS RBAC model; it exists because insurance audit/compliance
 * requires a read-only party who can see the FULL history (including AI
 * agent proposals that were rejected) without being able to change anything.
 */
public enum ClaimRole {
    POLICYHOLDER(EnumSet.of(ClaimPermission.VIEW, ClaimPermission.SUBMIT_DOCUMENTS, ClaimPermission.VIEW_PARTIES)),
    ADJUSTER(EnumSet.of(ClaimPermission.VIEW, ClaimPermission.SUBMIT_DOCUMENTS, ClaimPermission.UPDATE_STATUS,
            ClaimPermission.VIEW_PARTIES, ClaimPermission.MANAGE_PARTIES)),
    AUDITOR(EnumSet.of(ClaimPermission.VIEW, ClaimPermission.VIEW_PARTIES, ClaimPermission.VIEW_AUDIT_TRAIL));

    private final Set<ClaimPermission> permissions;

    ClaimRole(Set<ClaimPermission> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(ClaimPermission permission) {
        return permissions.contains(permission);
    }
}
