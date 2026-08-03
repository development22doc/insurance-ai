package com.claimassist.platform.claims_service.service.query.impl;

import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.mapper.ClaimMapper;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.security.SecurityExpressions;
import com.claimassist.platform.claims_service.service.query.ClaimQueryService;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimQueryServiceImpl implements ClaimQueryService {

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository claimStatusHistoryRepository;
    private final ClaimMapper claimMapper;
    private final CurrentUserProvider currentUserProvider;
    private final SecurityExpressions securityExpressions;

    @Override
    public List<ClaimSummaryResponse> getMyClaims() {
        Long userId = currentUserProvider.getCurrentUserId();
        return claimRepository.findAllAccessibleByUser(userId).stream()
                .map(p -> claimMapper.toClaimSummaryResponse(p.getClaim(), p.getRole()))
                .toList();
    }

    @Override
    @PreAuthorize("@security.canView(#claimId)")
    public ClaimSummaryResponse getClaimById(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        Long userId = currentUserProvider.getCurrentUserId();
        var role = claimRepository.findAllAccessibleByUser(userId).stream()
                .filter(p -> p.getClaim().getId().equals(claimId))
                .findFirst()
                .map(com.claimassist.platform.claims_service.repository.ClaimRepository.ClaimWithRoleProjection::getRole)
                .orElseThrow(() -> new BadRequestException("Not a party to this claim"));
        return claimMapper.toClaimSummaryResponse(claim, role);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
            cacheNames = com.claimassist.platform.claims_service.config.RedisCacheConfig.CLAIM_STATUS_CACHE,
            key = "#claimId")
    public ClaimStatusDto getClaimStatusWithHistory(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));

        List<ClaimStatusHistory> history = claimStatusHistoryRepository.findByClaimIdOrderByChangedAtAsc(claimId);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

        List<ClaimStatusDto.StatusHistoryEntry> entries = history.stream()
                .map(h -> new ClaimStatusDto.StatusHistoryEntry(
                        h.getFromStatus(), h.getToStatus(), h.getChangedBy(), formatter.format(h.getChangedAt())))
                .toList();

        return new ClaimStatusDto(
                claim.getId(), claim.getPolicyId(), claim.getClaimNumber(), claim.getStatus().name(), claim.getIncidentType(),
                claim.getEstimatedAmountCents(), claim.getApprovedAmountCents(), entries
        );
    }

    @Override
    public boolean hasPermission(Long claimId, ClaimPermission permission) {
        return securityExpressions.hasPermission(claimId, permission);
    }

    @Override
    public boolean hasPermissionForUser(Long claimId, Long userId, ClaimPermission permission) {
        return securityExpressions.hasPermissionForUser(claimId, userId, permission);
    }
}
