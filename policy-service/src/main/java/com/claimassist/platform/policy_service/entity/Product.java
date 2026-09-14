package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 64)
    String code;

    @Column(nullable = false, length = 128)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    ProductType type;

    @Column(length = 1000)
    String description;

    @Column(nullable = false, length = 32)
    @Builder.Default
    String status = "ACTIVE";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    Instant updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @Builder.Default
    List<Plan> plans = new ArrayList<>();
}
