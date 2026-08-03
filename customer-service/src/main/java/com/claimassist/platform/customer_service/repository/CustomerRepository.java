    package com.claimassist.platform.customer_service.repository;

    import com.claimassist.platform.customer_service.entity.Customer;
    import org.springframework.data.jpa.repository.JpaRepository;

    import java.util.Optional;

    public interface CustomerRepository extends JpaRepository<Customer, Long> {
        Optional<Customer> findByUsername(String username);
    }
