package com.claimassist.platform.customer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.customer_service.dto.customer.UpdateCustomerRequest;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import com.claimassist.platform.customer_service.service.CustomerService;

import jakarta.annotation.Resource;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(PostgresIntegrationTestConfig.class)
class CustomerPostgresTransactionIT {

    @Resource
    CustomerRepository customerRepository;

    @Resource
    CoveragePlanRepository coveragePlanRepository;

    @Resource
    PolicyRepository policyRepository;

    @Resource
    CustomerService customerService;

    @Resource
    TransactionTemplate transactionTemplate;

    @Test
    void notFound_findById_returnsEmpty() {
        assertThat(customerRepository.findById(9_999_999L)).isEmpty();
    }

    @Test
    void notFound_updateCustomer_throwsResourceNotFoundException() {
        UpdateCustomerRequest request = new UpdateCustomerRequest("Ghost User");

        assertThatThrownBy(() -> customerService.updateCustomer(9_999_999L, request, 9_999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer");
    }

    @Test
    void notFound_deleteCustomer_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> customerService.deleteCustomer(9_999_999L, 9_999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer");
    }

    @Test
    void constraint_duplicateUsername_throwsDataIntegrityViolationException() {
        String username = "dup-user-" + UUID.randomUUID() + "@example.com";

        Customer first = new Customer();
        first.setUsername(username);
        first.setFullName("First User");
        first.setKeycloakId("keycloak-dup-1-" + UUID.randomUUID());
        customerRepository.saveAndFlush(first);

        Customer duplicate = new Customer();
        duplicate.setUsername(username);
        duplicate.setFullName("Second User");
        duplicate.setKeycloakId("keycloak-dup-2-" + UUID.randomUUID());

        assertThatThrownBy(() -> customerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(customerRepository.findByUsername(username))
                .isPresent()
                .get()
                .extracting(Customer::getFullName)
                .isEqualTo("First User");
    }

    @Test
    void constraint_notNullViolation_throwsDataIntegrityViolationException() {
        String username = "null-field-" + UUID.randomUUID() + "@example.com";

        Customer customer = new Customer();
        customer.setUsername(username);
        customer.setKeycloakId("keycloak-null-field-" + UUID.randomUUID());

        assertThatThrownBy(() -> customerRepository.saveAndFlush(customer))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(customerRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void transaction_rollbackOnConstraintViolation_doesNotLeavePartialState() {
        String username = "tx-rollback-" + UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Customer customer = new Customer();
            customer.setUsername(username);
            customer.setFullName("Rollback User");
            customer.setKeycloakId("keycloak-tx-rollback-" + UUID.randomUUID());
            customerRepository.saveAndFlush(customer);

            Customer duplicate = new Customer();
            duplicate.setUsername(username);
            duplicate.setFullName("Duplicate User");
            duplicate.setKeycloakId("keycloak-tx-rollback-2-" + UUID.randomUUID());
            customerRepository.saveAndFlush(duplicate);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(customerRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void transaction_successfulCommit_persistsAllEntities() {
        String username = "tx-commit-" + UUID.randomUUID() + "@example.com";

        Customer saved = transactionTemplate.execute(status -> {
            Customer customer = new Customer();
            customer.setUsername(username);
            customer.setFullName("Committed User");
            customer.setKeycloakId("keycloak-tx-commit-" + UUID.randomUUID());
            return customerRepository.save(customer);
        });

        assertThat(saved).isNotNull();
        assertThat(customerRepository.findById(saved.getId())).isPresent();
        assertThat(customerRepository.findByUsername(username)).isPresent();
    }

    @Test
    @Transactional
    void transaction_policyDuplicateWithinTransactionalMethod_rollsBackEntireTestTransaction() {
        String policyNumber = "POL-TX-ROLLBACK-" + UUID.randomUUID();

        Customer customer = new Customer();
        customer.setUsername("policy-tx-" + UUID.randomUUID() + "@example.com");
        customer.setFullName("Policy Tx User");
        customer.setKeycloakId("keycloak-policy-tx-" + UUID.randomUUID());
        Customer savedCustomer = customerRepository.saveAndFlush(customer);

        CoveragePlan coveragePlan = new CoveragePlan();
        coveragePlan.setName("Tx Plan " + UUID.randomUUID());
        coveragePlan.setProductType("AUTO");
        coveragePlan.setAnnualPremiumCents(100000L);
        coveragePlan.setDeductibleCents(50000L);
        coveragePlan.setCoverageLimitCents(5000000L);
        CoveragePlan savedCoveragePlan = coveragePlanRepository.saveAndFlush(coveragePlan);

        Policy firstPolicy = new Policy();
        firstPolicy.setCustomer(savedCustomer);
        firstPolicy.setCoveragePlan(savedCoveragePlan);
        firstPolicy.setPolicyNumber(policyNumber);
        firstPolicy.setStatus("ACTIVE");
        firstPolicy.setEffectiveDate(Instant.now());
        policyRepository.saveAndFlush(firstPolicy);

        Policy duplicatePolicy = new Policy();
        duplicatePolicy.setCustomer(savedCustomer);
        duplicatePolicy.setCoveragePlan(savedCoveragePlan);
        duplicatePolicy.setPolicyNumber(policyNumber);
        duplicatePolicy.setStatus("PENDING");
        duplicatePolicy.setEffectiveDate(Instant.now());

        assertThatThrownBy(() -> policyRepository.saveAndFlush(duplicatePolicy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateFailure_duplicateKeycloakId_doesNotCorruptExistingRow() {
        String sharedKeycloakId = "shared-keycloak-" + UUID.randomUUID();

        Customer existing = new Customer();
        existing.setUsername("existing-" + UUID.randomUUID() + "@example.com");
        existing.setFullName("Existing User");
        existing.setKeycloakId(sharedKeycloakId);
        customerRepository.saveAndFlush(existing);

        Customer other = new Customer();
        other.setUsername("other-" + UUID.randomUUID() + "@example.com");
        other.setFullName("Other User");
        other.setKeycloakId("other-keycloak-" + UUID.randomUUID());
        Customer savedOther = customerRepository.saveAndFlush(other);

        savedOther.setKeycloakId(sharedKeycloakId);

        assertThatThrownBy(() -> customerRepository.saveAndFlush(savedOther))
                .isInstanceOf(DataIntegrityViolationException.class);

        Customer reloaded = customerRepository.findById(savedOther.getId()).orElseThrow();
        assertThat(reloaded.getKeycloakId()).isNotEqualTo(sharedKeycloakId);
        assertThat(reloaded.getFullName()).isEqualTo("Other User");
    }
}
