package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimId(Long claimId);
}
