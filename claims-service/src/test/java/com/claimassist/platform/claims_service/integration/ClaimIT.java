package com.claimassist.platform.claims_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.entity.ClaimPartyId;
import com.claimassist.platform.claims_service.entity.ClaimDocument;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.repository.ClaimDocumentRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.repository.ClaimSummaryRow;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.enums.ClaimRole;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
class ClaimIT {

    @Resource
    ClaimRepository claimRepository;

    @Resource
    ClaimPartyRepository claimPartyRepository;

    @Resource
    ClaimDocumentRepository claimDocumentRepository;

    @Resource
    ClaimStatusHistoryRepository claimStatusHistoryRepository;

    @Test
    void crud_createAndReadClaim() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-TEST-001");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(com.claimassist.platform.common_lib.enums.ClaimStatus.SUBMITTED);
        claim.setIncidentDate(java.time.Instant.now());

        Claim saved = claimRepository.save(claim);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getClaimNumber()).isEqualTo("CLM-TEST-001");
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        Claim found = claimRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getClaimNumber()).isEqualTo("CLM-TEST-001");
    }

    @Test
    void crud_findByClaimNumber() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-TEST-002");
        claim.setPolicyId(1L);
        claim.setIncidentType("THEFT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claimRepository.save(claim);

        Claim found = claimRepository.findByClaimNumber("CLM-TEST-002").orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getIncidentType()).isEqualTo("THEFT");
    }

    @Test
    void constraint_uniqueClaimNumber_ShouldPass() {
        Claim c1 = new Claim();
        c1.setClaimNumber("UNIQUE-NUMBER-1");
        c1.setPolicyId(1L);
        c1.setIncidentType("ACCIDENT");
        c1.setStatus(ClaimStatus.SUBMITTED);
        claimRepository.save(c1);

        Claim c2 = new Claim();
        c2.setClaimNumber("UNIQUE-NUMBER-1");
        c2.setPolicyId(2L);
        c2.setIncidentType("THEFT");
        c2.setStatus(ClaimStatus.SUBMITTED);

        assertThatThrownBy(() -> claimRepository.save(c2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void relationship_claimAndParties_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-WITH-PARTIES");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        // Add claim parties
        ClaimPartyId partyId1 = new ClaimPartyId(savedClaim.getId(), 100L);
        ClaimParty party1 = new ClaimParty();
        party1.setId(partyId1);
        party1.setClaimRole(ClaimRole.POLICYHOLDER);
        party1.setAddedAt(Instant.now());
        claimPartyRepository.save(party1);

        ClaimPartyId partyId2 = new ClaimPartyId(savedClaim.getId(), 101L);
        ClaimParty party2 = new ClaimParty();
        party2.setId(partyId2);
        party2.setClaimRole(ClaimRole.ADJUSTER);
        party2.setAddedAt(Instant.now());
        claimPartyRepository.save(party2);

        assertThat(claimPartyRepository.findAll()).hasSize(2);
    }

    @Test
    @Transactional
    void relationship_claimAndDocuments_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-WITH-DOCS");
        claim.setPolicyId(1L);
        claim.setIncidentType("THEFT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        ClaimDocument document = new ClaimDocument();
        document.setClaim(savedClaim);
        document.setPath("/path/to/document.pdf");
        document.setMinioObjectKey("doc-123");
        document.setDocType("POLICE_REPORT");
        document.setOcrStatus("PENDING");
        document.setUploadedAt(Instant.now());
        ClaimDocument savedDocument = claimDocumentRepository.save(document);

        assertThat(savedDocument.getId()).isNotNull();
        assertThat(savedDocument.getClaim().getId()).isEqualTo(savedClaim.getId());
    }

    @Test
    @Transactional
    void relationship_claimAndStatusHistory_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-WITH-HISTORY");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        ClaimStatusHistory history = new ClaimStatusHistory();
        history.setClaimId(savedClaim.getId());
        history.setFromStatus("SUBMITTED");
        history.setToStatus("UNDER_REVIEW");
        history.setChangedBy("system");
        history.setNote("Initial review started");
        history.setChangedAt(Instant.now());
        ClaimStatusHistory savedHistory = claimStatusHistoryRepository.save(history);

        assertThat(savedHistory.getId()).isNotNull();
        assertThat(savedHistory.getClaimId()).isEqualTo(savedClaim.getId());
    }

    @Test
    void constraint_notNullFields_ShouldFail() {
        Claim claim = new Claim();
        // Missing required fields: claimNumber, incidentType, status, incidentDate

        assertThatThrownBy(() -> claimRepository.save(claim))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void updateClaimStatus_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-UPDATE-TEST");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        savedClaim.setStatus(ClaimStatus.UNDER_REVIEW);
        savedClaim.setEstimatedAmountCents(500000L);
        Claim updatedClaim = claimRepository.save(savedClaim);

        assertThat(updatedClaim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(updatedClaim.getEstimatedAmountCents()).isEqualTo(500000L);

        Claim found = claimRepository.findById(savedClaim.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void softDelete_withDeletedAt_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-SOFT-DELETE");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        // Soft delete
        savedClaim.setDeletedAt(Instant.now());
        claimRepository.save(savedClaim);

        Claim found = claimRepository.findById(savedClaim.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    @Test
    @Transactional
    void customQuery_findAllAccessibleByUser_ShouldWork() {
        Claim claim1 = new Claim();
        claim1.setClaimNumber("CLM-ACCESS-1");
        claim1.setPolicyId(1L);
        claim1.setIncidentType("ACCIDENT");
        claim1.setStatus(ClaimStatus.SUBMITTED);
        claim1.setIncidentDate(Instant.now());
        Claim savedClaim1 = claimRepository.save(claim1);

        Claim claim2 = new Claim();
        claim2.setClaimNumber("CLM-ACCESS-2");
        claim2.setPolicyId(2L);
        claim2.setIncidentType("THEFT");
        claim2.setStatus(ClaimStatus.SUBMITTED);
        claim2.setIncidentDate(Instant.now());
        Claim savedClaim2 = claimRepository.save(claim2);

        // Add parties
        ClaimPartyId partyId1 = new ClaimPartyId(savedClaim1.getId(), 200L);
        ClaimParty party1 = new ClaimParty();
        party1.setId(partyId1);
        party1.setClaimRole(ClaimRole.POLICYHOLDER);
        party1.setAddedAt(Instant.now());
        claimPartyRepository.save(party1);

        ClaimPartyId partyId2 = new ClaimPartyId(savedClaim2.getId(), 200L);
        ClaimParty party2 = new ClaimParty();
        party2.setId(partyId2);
        party2.setClaimRole(ClaimRole.POLICYHOLDER);
        party2.setAddedAt(Instant.now());
        claimPartyRepository.save(party2);

        List<ClaimSummaryRow> accessibleClaims =
                claimRepository.findAllAccessibleByUser(200L);

        assertThat(accessibleClaims).hasSize(2);
    }

    @Test
    @Transactional
    void customQuery_findAccessibleClaimById_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-FIND-BY-ID");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        ClaimPartyId partyId = new ClaimPartyId(savedClaim.getId(), 300L);
        ClaimParty party = new ClaimParty();
        party.setId(partyId);
        party.setClaimRole(ClaimRole.POLICYHOLDER);
        party.setAddedAt(Instant.now());
        claimPartyRepository.save(party);

        var accessibleClaim = claimRepository.findAccessibleClaimById(savedClaim.getId(), 300L);
        assertThat(accessibleClaim).isPresent();
        assertThat(accessibleClaim.get().getClaimNumber()).isEqualTo("CLM-FIND-BY-ID");

        var unauthorizedClaim = claimRepository.findAccessibleClaimById(savedClaim.getId(), 999L);
        assertThat(unauthorizedClaim).isEmpty();
    }

    @Test
    void constraint_claimPartyCompositeKey_ShouldWork() {
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-COMPOSITE-KEY");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        Claim savedClaim = claimRepository.save(claim);

        ClaimPartyId partyId = new ClaimPartyId(savedClaim.getId(), 400L);
        ClaimParty party1 = new ClaimParty();
        party1.setId(partyId);
        party1.setClaimRole(ClaimRole.POLICYHOLDER);
        party1.setAddedAt(Instant.now());
        claimPartyRepository.save(party1);

        // Try to add duplicate party (same claim and user)
        ClaimParty duplicateParty = new ClaimParty();
        duplicateParty.setId(partyId);
        duplicateParty.setClaimRole(ClaimRole.ADJUSTER);
        duplicateParty.setAddedAt(Instant.now());

        assertThatThrownBy(() -> claimPartyRepository.save(duplicateParty))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
