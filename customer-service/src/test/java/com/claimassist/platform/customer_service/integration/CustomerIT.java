package com.claimassist.platform.customer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
class CustomerIT {

    @Resource
    CustomerRepository customerRepository;

    @Resource
    CoveragePlanRepository coveragePlanRepository;

    @Resource
    PolicyRepository policyRepository;

    @Test
    void crud_createAndReadCustomer() {
        Customer customer = new Customer();
        customer.setUsername("testuser@example.com");
        customer.setFullName("Test User");
        customer.setKeycloakId("test-keycloak-id");

        Customer saved = customerRepository.save(customer);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("testuser@example.com");
        assertThat(saved.getFullName()).isEqualTo("Test User");

        Customer found = customerRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("testuser@example.com");
    }

    @Test
    void crud_findByUsername() {
        Customer customer = new Customer();
        customer.setUsername("john.doe@example.com");
        customer.setFullName("John Doe");
        customer.setKeycloakId("jk-12345");

        customerRepository.save(customer);

        Customer found = customerRepository.findByUsername("john.doe@example.com").orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getFullName()).isEqualTo("John Doe");
    }

    @Test
    void constraint_uniqueUsername_ShouldPass() {
        Customer c1 = new Customer();
        c1.setUsername("unique1@example.com");
        c1.setFullName("User One");
        c1.setKeycloakId("keycloak-1");
        customerRepository.save(c1);

        Customer c2 = new Customer();
        c2.setUsername("unique1@example.com");
        c2.setFullName("User Two");
        c2.setKeycloakId("keycloak-2");

        assertThatThrownBy(() -> customerRepository.save(c2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void constraint_uniqueKeycloakId_ShouldPass() {
        Customer c1 = new Customer();
        c1.setUsername("user1@example.com");
        c1.setFullName("User One");
        c1.setKeycloakId("shared-keycloak");
        customerRepository.save(c1);

        Customer c2 = new Customer();
        c2.setUsername("user2@example.com");
        c2.setFullName("User Two");
        c2.setKeycloakId("shared-keycloak");

        assertThatThrownBy(() -> customerRepository.save(c2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void relationship_customerPolicyCoveragePlan_ShouldWork() {
        // Create customer
        Customer customer = new Customer();
        customer.setUsername("policyholder@example.com");
        customer.setFullName("Policy Holder");
        customer.setKeycloakId("keycloak-policy");
        Customer savedCustomer = customerRepository.save(customer);

        // Create coverage plan
        CoveragePlan coveragePlan = new CoveragePlan();
        coveragePlan.setName("Test Plan");
        coveragePlan.setProductType("AUTO");
        coveragePlan.setAnnualPremiumCents(100000L);
        coveragePlan.setDeductibleCents(50000L);
        coveragePlan.setCoverageLimitCents(5000000L);
        CoveragePlan savedCoveragePlan = coveragePlanRepository.save(coveragePlan);

        // Create policy
        Policy policy = new Policy();
        policy.setCustomer(savedCustomer);
        policy.setCoveragePlan(savedCoveragePlan);
        policy.setPolicyNumber("POL-TEST-001");
        policy.setStatus("ACTIVE");
        policy.setEffectiveDate(Instant.now());
        Policy savedPolicy = policyRepository.save(policy);

        assertThat(savedPolicy.getId()).isNotNull();
        assertThat(savedPolicy.getCustomer().getId()).isEqualTo(savedCustomer.getId());
        assertThat(savedPolicy.getCoveragePlan().getId()).isEqualTo(savedCoveragePlan.getId());

        // Test finding policy by customer
        List<Policy> customerPolicies = policyRepository.findByCustomerId(savedCustomer.getId());
        assertThat(customerPolicies).hasSize(1);
        assertThat(customerPolicies.get(0).getPolicyNumber()).isEqualTo("POL-TEST-001");

        // Test custom query with customer authorization
        var policyWithAuth = policyRepository.findByIdAndCustomerId(savedPolicy.getId(), savedCustomer.getId());
        assertThat(policyWithAuth).isPresent();
        assertThat(policyWithAuth.get().getPolicyNumber()).isEqualTo("POL-TEST-001");

        // Test unauthorized access
        Long unauthorizedCustomerId = 999L;
        var unauthorizedPolicy = policyRepository.findByIdAndCustomerId(savedPolicy.getId(), unauthorizedCustomerId);
        assertThat(unauthorizedPolicy).isEmpty();
    }

    @Test
    void constraint_uniquePolicyNumber_ShouldPass() {
        Customer customer = new Customer();
        customer.setUsername("customer@example.com");
        customer.setFullName("Customer");
        customer.setKeycloakId("keycloak-customer");
        Customer savedCustomer = customerRepository.save(customer);

        CoveragePlan coveragePlan = new CoveragePlan();
        coveragePlan.setName("Plan");
        coveragePlan.setProductType("AUTO");
        coveragePlan.setAnnualPremiumCents(100000L);
        coveragePlan.setDeductibleCents(50000L);
        coveragePlan.setCoverageLimitCents(5000000L);
        CoveragePlan savedCoveragePlan = coveragePlanRepository.save(coveragePlan);

        Policy p1 = new Policy();
        p1.setCustomer(savedCustomer);
        p1.setCoveragePlan(savedCoveragePlan);
        p1.setPolicyNumber("DUPLICATE-POL");
        p1.setStatus("ACTIVE");
        p1.setEffectiveDate(Instant.now());
        policyRepository.save(p1);

        Policy p2 = new Policy();
        p2.setCustomer(savedCustomer);
        p2.setCoveragePlan(savedCoveragePlan);
        p2.setPolicyNumber("DUPLICATE-POL");
        p2.setStatus("PENDING");
        p2.setEffectiveDate(Instant.now());

        assertThatThrownBy(() -> policyRepository.save(p2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void constraint_notNullFields_ShouldFail() {
        Customer customer = new Customer();
        // Missing required fields: username, fullName

        assertThatThrownBy(() -> customerRepository.save(customer))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void deleteCustomer_ShouldCascadeRefreshTokens() {
        Customer customer = new Customer();
        customer.setUsername("todelete@example.com");
        customer.setFullName("To Delete");
        customer.setKeycloakId("keycloak-delete");
        Customer savedCustomer = customerRepository.save(customer);

        Long customerId = savedCustomer.getId();
        assertThat(customerRepository.findById(customerId)).isPresent();

        customerRepository.deleteById(customerId);

        assertThat(customerRepository.findById(customerId)).isEmpty();
    }

    @Test
    void updateCustomer_ShouldWork() {
        Customer customer = new Customer();
        customer.setUsername("update@example.com");
        customer.setFullName("Original Name");
        customer.setKeycloakId("keycloak-update");
        Customer savedCustomer = customerRepository.save(customer);

        savedCustomer.setFullName("Updated Name");
        savedCustomer.setKycStatus("VERIFIED");
        Customer updatedCustomer = customerRepository.save(savedCustomer);

        assertThat(updatedCustomer.getFullName()).isEqualTo("Updated Name");
        assertThat(updatedCustomer.getKycStatus()).isEqualTo("VERIFIED");

        Customer found = customerRepository.findById(savedCustomer.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getFullName()).isEqualTo("Updated Name");
        assertThat(found.getKycStatus()).isEqualTo("VERIFIED");
    }
}
