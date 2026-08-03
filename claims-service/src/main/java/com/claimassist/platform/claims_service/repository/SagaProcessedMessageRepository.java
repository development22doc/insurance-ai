package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.SagaProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaProcessedMessageRepository extends JpaRepository<SagaProcessedMessage, String> {
}

