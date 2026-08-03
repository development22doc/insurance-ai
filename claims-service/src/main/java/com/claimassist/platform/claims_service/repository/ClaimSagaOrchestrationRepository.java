package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimSagaOrchestrationRepository extends JpaRepository<ClaimSagaOrchestration, Long> {

    Optional<ClaimSagaOrchestration> findBySagaId(String sagaId);

    @Query("SELECT s FROM ClaimSagaOrchestration s WHERE s.status IN :statuses AND s.expiresAt <= :now")
    List<ClaimSagaOrchestration> findTimedOutSagas(@Param("statuses") Collection<SagaOrchestrationStatus> statuses,
                                                   @Param("now") Instant now);

    @Query("SELECT s FROM ClaimSagaOrchestration s WHERE s.status = :status AND s.updatedAt <= :before")
    List<ClaimSagaOrchestration> findRecoverableSagas(@Param("status") SagaOrchestrationStatus status,
                                                      @Param("before") Instant before);
}

