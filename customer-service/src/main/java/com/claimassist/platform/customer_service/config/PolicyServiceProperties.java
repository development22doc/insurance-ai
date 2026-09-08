package com.claimassist.platform.customer_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Optional;
import jakarta.annotation.PostConstruct;

@ConfigurationProperties(prefix = "policy-service")
public class PolicyServiceProperties {

    /**
     * Optional coverage mappings keyed by CoveragePlan id.
     * Example (application.yaml):
     * policy-service:
     *   coverage-mapping:
     *     1:
     *       productCode: AUTO
     *       planCode: STANDARD
     *       coverageCode: COMPREHENSIVE
     */
    private Map<String, Mapping> coverageMapping;

    // When true, customer-service will delegate reads to Policy Service internal APIs where possible.
    // Default: false (preserve legacy reads). Enable per environment during staged cutover.
    private boolean readDelegationEnabled = false;

    public Map<String, Mapping> getCoverageMapping() {
        return coverageMapping;
    }

    public void setCoverageMapping(Map<String, Mapping> coverageMapping) {
        this.coverageMapping = coverageMapping;
    }

    public Optional<Mapping> getMappingForCoveragePlan(Long coveragePlanId) {
        if (coverageMapping == null) return Optional.empty();
        Mapping m = coverageMapping.get(String.valueOf(coveragePlanId));
        return Optional.ofNullable(m);
    }

    public boolean isReadDelegationEnabled() {
        return readDelegationEnabled;
    }

    public void setReadDelegationEnabled(boolean readDelegationEnabled) {
        this.readDelegationEnabled = readDelegationEnabled;
    }

    /**
     * Validate configuration at startup. Ensures keys are numeric and mapping values are complete.
     * Public so tests can call it directly without starting Spring context.
     */
    @PostConstruct
    public void validate() {
        if (coverageMapping == null) return;
        for (Map.Entry<String, Mapping> e : coverageMapping.entrySet()) {
            String key = e.getKey();
            Mapping m = e.getValue();
            // keys must be numeric coveragePlan ids
            try {
                Long.parseLong(key);
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("policy-service.coverage-mapping contains a non-numeric key: '" + key + "'");
            }
            if (m == null) {
                throw new IllegalStateException("policy-service.coverage-mapping for coveragePlanId '" + key + "' is null");
            }
            if (m.getProductCode() == null || m.getProductCode().isBlank()) {
                throw new IllegalStateException("policy-service.coverage-mapping[" + key + "].productCode is required and must not be blank");
            }
            if (m.getPlanCode() == null || m.getPlanCode().isBlank()) {
                throw new IllegalStateException("policy-service.coverage-mapping[" + key + "].planCode is required and must not be blank");
            }
            if (m.getCoverageCode() == null || m.getCoverageCode().isBlank()) {
                throw new IllegalStateException("policy-service.coverage-mapping[" + key + "].coverageCode is required and must not be blank");
            }
        }
    }

    public static class Mapping {
        private String productCode;
        private String planCode;
        private String coverageCode;

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getPlanCode() {
            return planCode;
        }

        public void setPlanCode(String planCode) {
            this.planCode = planCode;
        }

        public String getCoverageCode() {
            return coverageCode;
        }

        public void setCoverageCode(String coverageCode) {
            this.coverageCode = coverageCode;
        }
    }
}
