package com.claimassist.platform.customer_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String username; // email, also login id - mirrored as the Keycloak user's username/email

    /**
     * Keycloak's own user id (a UUID) for this customer, so customer-service
     * can call the Keycloak Admin API for this user later (password reset,
     * disable account, etc.) without a lookup. NOT used as the JWT identity
     * claim - see CurrentUserProvider for why "userId" (this entity's own
     * `id`) is the claim actually used platform-wide.
     */
    @Column(unique = true)
    String keycloakId;

    @Column(nullable = false)
    String fullName;

    String stripeCustomerId;

    @Builder.Default
    String kycStatus = "PENDING"; // PENDING / VERIFIED / REJECTED
}
