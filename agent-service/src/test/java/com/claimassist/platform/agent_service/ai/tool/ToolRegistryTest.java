package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private final ToolRegistry registry = new ToolRegistry();

    @Test
    void registersAllFourTools() {
        assertThat(registry.all()).extracting(ToolMetadata::name)
                .containsExactlyInAnyOrder(
                        ToolRegistry.CLAIM_STATUS,
                        ToolRegistry.POLICY_COVERAGE,
                        ToolRegistry.CLAIM_DOCUMENTS,
                        ToolRegistry.PROPOSE_CLAIM_UPDATE);
    }

    @Test
    void readToolsAreClassifiedReadWithViewPermission() {
        for (String name : new String[]{ToolRegistry.CLAIM_STATUS, ToolRegistry.POLICY_COVERAGE, ToolRegistry.CLAIM_DOCUMENTS}) {
            assertThat(registry.riskLevel(name)).isEqualTo(ToolRiskLevel.READ);
            assertThat(registry.requiredPermission(name)).isEqualTo(ClaimPermission.VIEW);
        }
    }

    @Test
    void writeToolIsClassifiedWriteWithUpdateStatusPermission() {
        assertThat(registry.riskLevel(ToolRegistry.PROPOSE_CLAIM_UPDATE)).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(registry.requiredPermission(ToolRegistry.PROPOSE_CLAIM_UPDATE)).isEqualTo(ClaimPermission.UPDATE_STATUS);
    }

    @Test
    void readToolsAreRetryableAtGatewayLayerWriteIsNot() {
        assertThat(registry.maxRetries(ToolRegistry.CLAIM_STATUS)).isEqualTo(2);
        assertThat(registry.maxRetries(ToolRegistry.POLICY_COVERAGE)).isEqualTo(2);
        assertThat(registry.maxRetries(ToolRegistry.CLAIM_DOCUMENTS)).isEqualTo(2);
        // Write/proposal must not be retried at this layer (idempotency preserved).
        assertThat(registry.maxRetries(ToolRegistry.PROPOSE_CLAIM_UPDATE)).isZero();
    }

    @Test
    void proposeToolIsIdempotentReadToolsAreNot() {
        assertThat(registry.isIdempotent(ToolRegistry.PROPOSE_CLAIM_UPDATE)).isTrue();
        assertThat(registry.isIdempotent(ToolRegistry.CLAIM_STATUS)).isFalse();
        assertThat(registry.isIdempotent(ToolRegistry.CLAIM_DOCUMENTS)).isFalse();
        assertThat(registry.isIdempotent(ToolRegistry.POLICY_COVERAGE)).isFalse();
    }

    @Test
    void toolsDeclareProvenanceSource() {
        assertThat(registry.metadata(ToolRegistry.CLAIM_STATUS)).map(ToolMetadata::source)
                .contains("claims-service");
        assertThat(registry.metadata(ToolRegistry.POLICY_COVERAGE)).map(ToolMetadata::source)
                .contains("policy-service");
        assertThat(registry.metadata(ToolRegistry.PROPOSE_CLAIM_UPDATE)).map(ToolMetadata::source)
                .contains("agent-service");
    }

    @Test
    void unknownToolFallsBackToReadAndEmptyTimeout() {
        assertThat(registry.riskLevel("does_not_exist")).isEqualTo(ToolRiskLevel.READ);
        assertThat(registry.timeoutMs("does_not_exist")).isEqualTo(Optional.empty());
        assertThat(registry.maxRetries("does_not_exist")).isZero();
    }

    @Test
    void perToolTimeoutIsEmptyWhenUsingGlobalDefault() {
        // All current tools delegate to the global agent.ai.tool-timeout-ms.
        for (ToolMetadata m : registry.all()) {
            assertThat(registry.timeoutMs(m.name())).isEmpty();
        }
    }

    @Test
    void metadataRejectsBlankNameAndMissingPermission() {
        assertThatThrownBy(() -> new ToolMetadata("", "d", ToolRiskLevel.READ, ClaimPermission.VIEW,
                0L, 0, false, "s", "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolMetadata("t", "d", ToolRiskLevel.READ, null,
                0L, 0, false, "s", "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writeClassificationFlag() {
        assertThat(registry.metadata(ToolRegistry.PROPOSE_CLAIM_UPDATE)).map(ToolMetadata::isWrite).contains(true);
        assertThat(registry.metadata(ToolRegistry.CLAIM_STATUS)).map(ToolMetadata::isWrite).contains(false);
    }
}