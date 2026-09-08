package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Product;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    Optional<Product> findByCodeIgnoreCase(String code);

    /**
     * Find all products for public catalog query.
     */
    List<Product> findAllByOrderByCreatedAtDesc();
}
