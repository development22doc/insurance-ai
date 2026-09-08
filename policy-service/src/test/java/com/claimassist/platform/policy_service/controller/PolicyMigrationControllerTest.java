package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.migration.LegacyPolicyDataset;
import com.claimassist.platform.policy_service.migration.LegacyPolicyDryRunReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyMigrationController.class)
class PolicyMigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.claimassist.platform.policy_service.migration.LegacyPolicyDryRunService dryRunService;

    @MockBean
    private com.claimassist.platform.policy_service.migration.ConfigurationDrivenLegacyPlanMappingResolver mappingResolver;

    @MockBean
    private com.claimassist.platform.common_lib.security.CurrentUserProvider currentUserProvider;

    @Test
    void dryRunEndpoint_acceptsDataset_andReturnsReport() throws Exception {
        LegacyPolicyDataset dataset = new LegacyPolicyDataset("customer_service", "batch-1", null, "v1", null, java.util.List.of());
        LegacyPolicyDryRunReport report = new LegacyPolicyDryRunReport(0,0,0,0,0,0, java.util.List.of(), java.time.Instant.now(), "MISSING", false);

        when(dryRunService.dryRun(dataset, mappingResolver)).thenReturn(report);
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        mockMvc.perform(post("/api/v1/policies/migration/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dataset)))
                .andExpect(status().isForbidden());
    }
}
