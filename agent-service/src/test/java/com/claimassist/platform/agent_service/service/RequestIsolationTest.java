package com.claimassist.platform.agent_service.service;

import com.claimassist.platform.agent_service.ai.tool.ToolRegistry;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies request isolation: tool instances are created per request and share
 * no claim context across requests, so one user's claim data can never leak
 * into another request.
 */
class RequestIsolationTest {

    @Test
    void concurrentRequestsKeepTheirOwnClaimContext() throws InterruptedException {
        List<String> proposalsA = new ArrayList<>();
        List<String> proposalsB = new ArrayList<>();

        // Two independent tools for two different claims, each with its own collector.
        InsuranceAgentTools toolsA = toolsFor(101L, 11L, proposalsA);
        InsuranceAgentTools toolsB = toolsFor(202L, 22L, proposalsB);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        pool.submit(() -> { await(start); toolsA.proposeClaimUpdate("DOCS_REQUESTED", "blurry"); done.countDown(); });
        pool.submit(() -> { await(start); toolsB.proposeClaimUpdate("DOCS_REQUESTED", "blurry"); done.countDown(); });
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Each request's proposal only landed in its own collector.
        assertThat(proposalsA).hasSize(1).containsExactly("DOCS_REQUESTED/101");
        assertThat(proposalsB).hasSize(1).containsExactly("DOCS_REQUESTED/202");
    }

    private void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private InsuranceAgentTools toolsFor(long claimId, long policyId, List<String> proposals) {
        ClaimsServiceGateway claims = mock(ClaimsServiceGateway.class);
        CustomerServiceGateway customer = mock(CustomerServiceGateway.class);
        when(claims.checkPermission(anyLong(), any())).thenReturn(true);
        when(claims.getClaimStatus(claimId)).thenReturn(
                new ClaimStatusDto(claimId, policyId, "CLM", "SUBMITTED", "FIRE", 100L, null, List.of()));
        when(customer.getPolicyCoverage(policyId, claimId)).thenReturn(
                new PolicyCoverageDto(policyId, "P", "ACTIVE", "HOME", "B", 100L, 1000L, null));
        AtomicLong id = new AtomicLong(claimId);
        return new InsuranceAgentTools(claimId, policyId, claimId, claims, customer, new ToolRegistry(), 1000,
                p -> proposals.add(p.proposedStatus() + "/" + id.get()));
    }
}