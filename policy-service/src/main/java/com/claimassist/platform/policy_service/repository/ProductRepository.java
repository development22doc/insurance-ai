package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    List<Product> findByStatus(String status);

    Optional<Product> findByIdAndStatus(Long productId, String status);
}
