package com.claimassist.platform.customer_service.mapper;

import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.Policy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

    @Mapping(target = "coveragePlanName", source = "coveragePlan.name")
    @Mapping(target = "productType", source = "coveragePlan.productType")
    PolicyResponse toPolicyResponse(Policy policy);
}
