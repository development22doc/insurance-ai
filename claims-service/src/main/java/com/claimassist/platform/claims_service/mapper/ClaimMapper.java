package com.claimassist.platform.claims_service.mapper;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClaimMapper {

    ClaimResponse toClaimResponse(Claim claim);

    @Mapping(target = "role", source = "role")
    ClaimSummaryResponse toClaimSummaryResponse(Claim claim, ClaimRole role);
}
