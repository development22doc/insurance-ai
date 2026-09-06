package com.claimassist.platform.policy_service.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record LegacyPolicyDataset(
        String sourceSystem,
        String batchId,
        Instant extractedAt,
        String schemaVersion,
        String checksum,
        List<LegacyPolicyRecord> records
) {
    public LegacyPolicyDataset {
        sourceSystem = sourceSystem == null ? "customer_service" : sourceSystem.trim();
        batchId = batchId == null || batchId.isBlank() ? "legacy-policy-batch-" + UUID.randomUUID() : batchId;
        extractedAt = extractedAt == null ? Instant.now() : extractedAt;
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "legacy-policy-v1" : schemaVersion;
        records = records == null ? List.of() : List.copyOf(records);
    }

    public int recordCount() {
        return records.size();
    }

    public boolean hasChecksum() {
        return checksum != null && !checksum.isBlank();
    }

    public boolean isChecksumValid() {
        if (!hasChecksum()) {
            return false;
        }
        return computeChecksum().equalsIgnoreCase(checksum.trim());
    }

    public String checksumStatus() {
        if (!hasChecksum()) {
            return "MISSING";
        }
        return isChecksumValid() ? "VALID" : "INVALID";
    }

    public String computeChecksum() {
        String canonicalPayload = records.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LegacyPolicyRecord::legacyPolicyId, Comparator.nullsLast(Long::compareTo)))
                .map(this::canonicalRecord)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String canonicalRecord(LegacyPolicyRecord record) {
        return String.join("|",
                safe(record.legacyPolicyId()),
                safe(record.legacyCustomerId()),
                safe(record.legacyPolicyNumber()),
                safe(record.legacyCoveragePlanId()),
                safe(record.legacyCoveragePlanName()),
                safe(record.productType()),
                safe(record.annualPremiumCents()),
                safe(record.deductibleCents()),
                safe(record.coverageLimitCents()),
                safe(record.stripePriceId()),
                safe(record.stripeSubscriptionId()),
                safe(record.effectiveDate()),
                safe(record.renewalDate()),
                safe(record.legacyStatus()),
                safe(record.sourceSystem()));
    }

    private String safe(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
