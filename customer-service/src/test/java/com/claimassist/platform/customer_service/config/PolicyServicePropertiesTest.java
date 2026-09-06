package com.claimassist.platform.customer_service.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyServicePropertiesTest {

    @Test
    void validMappingDoesNotThrow() {
        PolicyServiceProperties props = new PolicyServiceProperties();
        Map<String, PolicyServiceProperties.Mapping> m = new HashMap<>();
        PolicyServiceProperties.Mapping mm = new PolicyServiceProperties.Mapping();
        mm.setProductCode("AUTO");
        mm.setPlanCode("STANDARD");
        mm.setCoverageCode("COMPREHENSIVE");
        m.put("1", mm);
        props.setCoverageMapping(m);

        assertDoesNotThrow(props::validate);
    }

    @Test
    void nonNumericKeyThrows() {
        PolicyServiceProperties props = new PolicyServiceProperties();
        Map<String, PolicyServiceProperties.Mapping> m = new HashMap<>();
        PolicyServiceProperties.Mapping mm = new PolicyServiceProperties.Mapping();
        mm.setProductCode("AUTO");
        mm.setPlanCode("STANDARD");
        mm.setCoverageCode("COMPREHENSIVE");
        m.put("one", mm);
        props.setCoverageMapping(m);

        assertThrows(IllegalStateException.class, props::validate);
    }

    @Test
    void missingFieldThrows() {
        PolicyServiceProperties props = new PolicyServiceProperties();
        Map<String, PolicyServiceProperties.Mapping> m = new HashMap<>();
        PolicyServiceProperties.Mapping mm = new PolicyServiceProperties.Mapping();
        mm.setProductCode("");
        mm.setPlanCode("STANDARD");
        mm.setCoverageCode("COMPREHENSIVE");
        m.put("2", mm);
        props.setCoverageMapping(m);

        assertThrows(IllegalStateException.class, props::validate);
    }
}