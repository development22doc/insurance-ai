package com.claimassist.platform.claims_service.cqrs;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.cache.CacheService;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS Read Model Synchronizer - listens to domain events and updates
 * the read model (MongoDB) and cache to maintain CQRS separation.
 *
 * This service processes events published to Kafka and ensures that
 * the read-optimized views stay in sync with the write model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CqrsReadModelSynchronizer {

    private final ClaimRepository claimRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for claim update response events and synchronize read model.
     * This is called after a claim has been successfully updated and the
     * event has been published to Kafka.
     */
    @Transactional
    @KafkaListener(
            topics = "claim-update-response-event",
            groupId = "cqrs-read-model-sync-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void synchronizeClaimReadModelOnUpdate(String rawMessage) throws Exception {
        try {
            ClaimUpdateResponseEvent event = objectMapper.readValue(rawMessage, ClaimUpdateResponseEvent.class);

            if (!event.success() || event.claimId() == null) {
                log.debug("Skipping read model sync for failed or incomplete event: {}", event.sagaId());
                return;
            }

            // Invalidate claim cache to force refresh on next read
            cacheService.invalidateClaimCache(event.claimId());

            log.info("CQRS: Invalidated read model cache for claim {}", event.claimId());

        } catch (Exception e) {
            log.error("Error synchronizing read model for claim update response", e);
            throw e;
        }
    }

    /**
     * Synchronize read model when a saga orchestration completes.
     * Updates claim status and related views in the read model.
     */
    @Transactional
    @KafkaListener(
            topics = "claim-saga-orchestration-result-event",
            groupId = "cqrs-read-model-sync-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void synchronizeClaimReadModelOnSagaCompletion(String rawMessage) throws Exception {
        try {
            // Parse the orchestration result event
            var result = objectMapper.readValue(rawMessage, OrchestrationResultDTO.class);

            if (result.claimId() != null) {
                // Invalidate all claim-related caches to ensure read consistency
                cacheService.invalidateClaimCache(result.claimId());
                log.info("CQRS: Invalidated read model for saga completion: claimId={}, status={}",
                        result.claimId(), result.status());
            }

        } catch (Exception e) {
            log.error("Error synchronizing read model for saga orchestration result", e);
            throw e;
        }
    }

    /**
     * DTO for parsing orchestration result events.
     */
    public record OrchestrationResultDTO(
            String sagaId,
            Long claimId,
            String status,
            String detail
    ) {}
}

