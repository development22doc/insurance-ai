package com.claimassist.platform.claims_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Metadata-only row - the actual file bytes live in MinIO under
 * "<claimId>/<path>", exactly like ProjectFile in the Lovable clone. ocrStatus/
 * extractedText/fraudSignalScore are populated asynchronously by the K8s
 * document-processing worker pool (see ClaimDocumentProcessingService) after
 * upload - PENDING until then.
 */
@Entity
@Table(name = "claim_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    Claim claim;

    @Column(nullable = false)
    String path;

    @Column(nullable = false)
    String minioObjectKey;

    @Column(nullable = false)
    String docType; // PHOTO / POLICE_REPORT / MEDICAL_BILL / REPAIR_ESTIMATE

    @Builder.Default
    String ocrStatus = "PENDING";

    @Lob
    @Column(columnDefinition = "text")
    String extractedText;

    Double fraudSignalScore;

    @Builder.Default
    Instant uploadedAt = Instant.now();
}
