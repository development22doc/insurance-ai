# Security Documentation

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

The ClaimAssist AI Platform implements a comprehensive security architecture covering authentication, authorization, encryption, and zero-trust networking.

---

## Authentication

### OAuth2 / OpenID Connect

**Provider:** Keycloak 26.0

**Flow:** Authorization Code + PKCE (for web clients)

#### User Login Flow

```
1. User accesses web application
   GET /dashboard

2. Web app initiates OAuth2 flow
   GET http://keycloak:8180/realms/claimassist/protocol/openid-connect/auth
   ?client_id=claimassist-web-app
   &redirect_uri=http://localhost:3000/callback
   &response_type=code
   &scope=openid profile email
   &state=random_state_value
   &code_challenge=BASE64URL(SHA256(code_verifier))
   &code_challenge_method=S256

3. User authenticates with Keycloak
   Submits username/password

4. Keycloak redirects with authorization code
   GET http://localhost:3000/callback?code=AUTH_CODE&state=random_state_value

5. Web app backend exchanges code for tokens
   POST http://keycloak:8180/realms/claimassist/protocol/openid-connect/token
   {
     grant_type: "authorization_code",
     client_id: "claimassist-web-app",
     client_secret: "app-secret",
     code: "AUTH_CODE",
     redirect_uri: "http://localhost:3000/callback",
     code_verifier: "original_code_verifier"
   }

6. Keycloak returns tokens
   {
     access_token: "eyJhbGciOiJSUzI1NiIs...",
     refresh_token: "eyJhbGciOiJIUzI1NiIs...",
     expires_in: 3600,
     token_type: "Bearer"
   }

7. Web app stores tokens (access in memory, refresh in secure cookie)
8. Subsequent API calls include access token
   GET http://api-gateway/claims
   Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

### Service-to-Service Authentication

**Flow:** OAuth2 Client Credentials

#### Internal Service Authentication

```
Service A needs to call Service B:

1. Service A requests token from Keycloak
   POST http://keycloak:8180/realms/claimassist/protocol/openid-connect/token
   {
     grant_type: "client_credentials",
     client_id: "claimassist-admin-service",
     client_secret: "SECRET_VALUE"
   }

2. Keycloak validates and returns JWT token
   {
     access_token: "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8va2V5Y2xvYWsuLi4iLCJzdWIiOiJjbGFpbWFzc2lzdC1hZG1pbi1zZXJ2aWNlIn0...",
     expires_in: 3600,
     token_type: "Bearer"
   }

3. Service A includes token in request to Service B
   GET http://customer-service:8081/api/policies/550e8400-e29b-41d4-a716-446655440000
   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

4. Service B validates token:
   a. Extract signature algorithm (alg: RS256)
   b. Fetch public key from Keycloak JWKS endpoint
   c. Verify signature
   d. Check expiration (exp claim)
   e. Verify issuer (iss claim)
   f. Extract scopes and roles
   g. Proceed if authorized

5. Service B processes request and returns data
```

### JWT Token Structure

**Access Token (signed):**
```json
{
  "jti": "abc123",
  "exp": 1600000000,
  "nbf": 0,
  "iat": 1599996400,
  "iss": "http://keycloak:8180/realms/claimassist",
  "aud": "account",
  "sub": "user-id",
  "typ": "Bearer",
  "azp": "claimassist-web-app",
  "session_state": "abc123",
  "acr": "1",
  "allowed-origins": [
    "http://localhost:3000"
  ],
  "realm_access": {
    "roles": [
      "CUSTOMER_USER",
      "default-roles-claimassist"
    ]
  },
  "resource_access": {
    "account": {
      "roles": [
        "manage-account",
        "manage-account-links",
        "view-profile"
      ]
    }
  },
  "scope": "openid profile email",
  "email_verified": true,
  "name": "John Doe",
  "preferred_username": "john@example.com",
  "given_name": "John",
  "family_name": "Doe",
  "email": "john@example.com"
}
```

**Refresh Token (opaque):**
- Used to obtain new access token without re-authentication
- Stored in secure HTTP-only cookie
- Longer lifetime than access token (7 days default)

### JWKS Validation

**Endpoint:** `http://keycloak:8180/realms/claimassist/protocol/openid-connect/certs`

**Spring Boot Configuration:**
```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: http://keycloak:8180/realms/claimassist
  jwk-set-uri: http://keycloak:8180/realms/claimassist/protocol/openid-connect/certs
```

**Validation Process:**
```
1. Receive JWT in Authorization header
2. Extract header (alg, kid)
3. GET JWKS from Keycloak
4. Find key with matching kid
5. Verify signature using key
6. Parse claims
7. Check expiration (exp)
8. Check not-before (nbf)
9. Verify issuer matches configured value
10. Extract roles and scopes
```

---

## Authorization

### Role-Based Access Control (RBAC)

**Roles Defined in Keycloak:**

```
CUSTOMER_USER
├── Permission: View own claims
├── Permission: Submit new claims
└── Permission: Upload documents

CLAIMS_OFFICER
├── Permission: View all claims
├── Permission: Update claim status
├── Permission: Approve/reject claims
└── Permission: Create claim notes

FRAUD_ANALYST
├── Permission: View claims with fraud scores
├── Permission: Access fraud analysis reports
└── Permission: Mark claims for investigation

ADMIN
└── Permission: All operations
```

### Authorization Implementation

**API Gateway Level:**
```java
// SecurityConfig
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/claims/**").hasRole("CUSTOMER_USER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated());
        return http.build();
    }
}
```

**Service Level:**
```java
// ClaimController
@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER_USER', 'CLAIMS_OFFICER')")
    public ResponseEntity<ClaimResponse> createClaim(
        @RequestBody CreateClaimRequest request,
        @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        // Verify user can create claim for given policy
        return claimService.createClaim(request, userId);
    }
    
    @PatchMapping("/{claimId}/status")
    @PreAuthorize("hasRole('CLAIMS_OFFICER')")
    public ResponseEntity<ClaimResponse> updateClaimStatus(
        @PathVariable String claimId,
        @RequestBody UpdateStatusRequest request) {
        
        return claimService.updateStatus(claimId, request);
    }
}
```

**Database Row-Level Security:**
```sql
-- Customer can only view own claims
WHERE claims.policy_id IN (
    SELECT policy_id 
    FROM policies 
    WHERE customer_id = current_user_id
)

-- Claims officer can view all claims assigned to them
WHERE claims.assigned_to = current_user_id 
   OR current_user_role = 'ADMIN'
```

---

## Transport Security

### HTTPS/TLS

**Configuration (Production):**
```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: claimassist
  port: 8443
```

**Kubernetes Ingress:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway-ingress
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - api.claimassist.com
    secretName: api-gateway-tls
  rules:
  - host: api.claimassist.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
```

### Security Headers

**API Gateway Configuration:**
```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
        same-site: strict
        max-age: 3600

spring.security:
  headers:
    content-security-policy: "default-src 'self'"
    content-type-options: nosniff
    xss-protection: "1; mode=block"
    frame-options: DENY
    hsts:
      enabled: true
      include-subdomains: true
      max-age: 31536000
      preload: true
```

**Response Headers Added:**
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

---

## Data Encryption

### At-Rest Encryption

**PostgreSQL (Transparent Data Encryption):**
```
Kubernetes Secret mounted as environment variable
Database encryption: Full-disk encryption at infrastructure level
```

**Sensitive Fields:**
```
Encrypted at application level before storing:
- Policy details
- Payment information
- Personal identifiable information (PII)
- Medical records
```

### In-Transit Encryption

**TLS/SSL:** All network communication encrypted
- Service-to-service: TLS 1.3
- Client-to-service: TLS 1.3
- Database connections: SSL enabled

**Kafka Communication:**
```
- Broker authentication: SASL/SSL (production)
- Producer encryption: Enabled
- Consumer encryption: Enabled
```

---

## Secret Management

### Kubernetes Secrets

**Secret Storage:**
```bash
kubectl create secret generic database-credentials \
  --from-literal=username=claimassist \
  --from-literal=password=SecurePassword123 \
  -n claimassist-core
```

**Reference in Deployment:**
```yaml
apiVersion: v1
kind: Deployment
metadata:
  name: claims-service
  namespace: claimassist-core
spec:
  template:
    spec:
      containers:
      - name: claims-service
        env:
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: password
```

### Keycloak Client Secrets

**Stored as Kubernetes Secret:**
```bash
kubectl create secret generic keycloak-clients \
  --from-literal=client-secret=AaBbCcDdEeFf123456 \
  -n claimassist-core
```

**Used in Configuration:**
```yaml
spring.security.oauth2.client.registration.internal-service:
  client-id: claimassist-admin-service
  client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

### Rotation Strategy

**Secrets Rotation:**
1. Create new secret version in Keycloak
2. Update Kubernetes Secret
3. Rolling restart of pods (old connections drain)
4. Delete old secret version

---

## Network Security

### Network Policies

**Default Deny Policy:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: claimassist-core
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
```

**Ingress for API Gateway:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-api-gateway-ingress
  namespace: claimassist-core
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
```

**Service-to-Service Communication:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-claims-to-customer-service
  namespace: claimassist-core
spec:
  podSelector:
    matchLabels:
      app: customer-service
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: claims-service
    ports:
    - protocol: TCP
      port: 8081
```

### Service Mesh (Optional)

For production, consider Istio for:
- Mutual TLS (mTLS) between services
- Advanced traffic policies
- Security policies
- Traffic visualization

---

## Input Validation & Sanitization

### Request Validation

**API Gateway:**
```java
@PostMapping("/claims")
public ResponseEntity<ClaimResponse> createClaim(
    @Valid @RequestBody CreateClaimRequest request) {
    // JSR-303 validation automatically applied
}
```

**DTO Validation Annotations:**
```java
public class CreateClaimRequest {
    @NotBlank(message = "Policy ID required")
    @Pattern(regexp = "^[0-9a-f-]{36}$", message = "Invalid UUID format")
    private String policyId;
    
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("999999.99")
    private BigDecimal amount;
    
    @NotBlank
    @Length(min = 10, max = 1000)
    private String description;
}
```

### Output Encoding

**JSON Response Encoding:**
```java
// Automatic XSS prevention via Jackson serialization
response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
```

### SQL Injection Prevention

**Parameterized Queries (JPA):**
```java
@Query("SELECT c FROM Claim c WHERE c.claimId = :claimId")
Optional<Claim> findById(@Param("claimId") String claimId);
```

---

## Audit & Compliance

### Audit Logging

**Logged Events:**
```
1. Authentication attempts (success/failure)
2. Authorization decisions (access granted/denied)
3. Sensitive data access
4. Configuration changes
5. Claims status updates
6. Document uploads
7. Report generation
```

**Log Format:**
```json
{
  "timestamp": "2026-08-04T10:30:45.123Z",
  "event_type": "CLAIM_STATUS_UPDATED",
  "user_id": "user@example.com",
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "old_status": "INITIATED",
  "new_status": "UNDER_REVIEW",
  "ip_address": "192.168.1.100",
  "user_agent": "Mozilla/5.0...",
  "result": "SUCCESS",
  "trace_id": "4e17d3a9c6b7f2d1"
}
```

### Compliance

**Implemented Controls:**
- ✅ Authentication & authorization
- ✅ Encryption (in-transit & at-rest)
- ✅ Audit logging
- ✅ Access control
- ✅ Data validation
- ✅ Security headers
- ✅ Secret management
- ✅ Network policies
- ✅ Rate limiting
- ✅ CORS controls

**Compliance Frameworks:**
- OWASP Top 10 mitigation
- GDPR data protection (with additional measures needed)
- HIPAA audit controls (partial, needs encryption key management)
- PCI DSS readiness (with additional payment controls)

---

## Security Best Practices

### Development

1. **Least Privilege:** Grant minimum required permissions
2. **Input Validation:** Validate all user inputs
3. **Secure Defaults:** Enable security features by default
4. **Fail Securely:** Return generic error messages
5. **Keep Dependencies Updated:** Run security scans regularly

### Deployment

1. **Secrets Management:** Use Kubernetes Secrets/Vault
2. **Network Isolation:** Implement network policies
3. **RBAC:** Enable Kubernetes RBAC
4. **Pod Security:** Use Pod Security Standards
5. **Monitoring:** Enable audit logging and monitoring

### Operations

1. **Secret Rotation:** Rotate secrets regularly
2. **Patching:** Apply security patches promptly
3. **Monitoring:** Monitor for suspicious activity
4. **Incident Response:** Have incident response procedures
5. **Backups:** Regular backups with encryption

---

## Security Checklist

- [ ] Keycloak is running and accessible
- [ ] JWKS endpoint is accessible from all services
- [ ] Client secrets stored in Kubernetes Secrets
- [ ] TLS/SSL configured for HTTPS
- [ ] Security headers configured
- [ ] Network policies deployed
- [ ] Audit logging enabled
- [ ] Rate limiting configured
- [ ] Input validation implemented
- [ ] Secrets rotation policy defined
- [ ] Security scans scheduled
- [ ] Incident response procedure documented

---

## Troubleshooting

### JWT Token Issues

**Invalid signature:**
```
Solution:
1. Verify Keycloak JWKS endpoint is accessible
2. Check issuer URI matches configuration
3. Verify token not tampered with
4. Check clock skew between services
```

**Token expired:**
```
Solution:
1. Use refresh token to get new access token
2. Configure token expiration appropriately
3. Implement token refresh logic in client
```

### Authorization Failures

**Access denied error:**
```
Solution:
1. Verify user has required role
2. Check role mapping in Keycloak
3. Verify @PreAuthorize annotation
4. Check custom authorization logic
```

---

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security Reference](https://spring.io/projects/spring-security)
- [OAuth2 Specification](https://tools.ietf.org/html/rfc6749)
- [OpenID Connect](https://openid.net/connect/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)


