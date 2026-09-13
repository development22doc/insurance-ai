package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Safe, idempotent diagnostic reconciliation runner.
 * Disabled by default. To run: start customer-service with
 * --app.reconcile.run=true [--app.reconcile.apply=true]
 *
 * By default this class only REPORTS suggested mappings. To APPLY patches
 * set app.reconcile.apply=true in a controlled environment. The apply path
 * will patch Keycloak user attributes only when a reliable Customer match
 * exists (by keycloakId or username). No new Customer rows are created.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakCustomerReconciliationRunner implements ApplicationRunner {

    private final KeycloakProperties keycloakProperties;
    private final CustomerRepository customerRepository;
    private final javax.sql.DataSource dataSource;
    private final org.springframework.core.env.Environment environment;

    // feature flags read from system properties / environment
    private boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("app.reconcile.run", System.getenv().getOrDefault("APP_RECONCILE_RUN", "false")));
    }

    private boolean isApply() {
        return Boolean.parseBoolean(System.getProperty("app.reconcile.apply", System.getenv().getOrDefault("APP_RECONCILE_APPLY", "false")));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isEnabled()) {
            log.debug("KeycloakCustomerReconciliationRunner disabled (app.reconcile.run not set)");
            return;
        }

        log.info("Starting Keycloak<->Customer reconciliation (apply={})", isApply());

        // Verify datasource matches the authoritative local-k8s DB before making any writes
        String jdbcUrl = null;
        try {
            if (dataSource != null) {
                java.sql.Connection conn = dataSource.getConnection();
                try {
                    jdbcUrl = conn.getMetaData().getURL();
                } finally {
                    conn.close();
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to read datasource metadata: {}", ex.getMessage());
        }

        String configuredUrl = environment.getProperty("spring.datasource.url", "");
        log.info("Datasource detected jdbc.url={} configuredUrl={}", jdbcUrl, configuredUrl);

        // Safety: ensure we can determine and connect to the actual datasource used by the running application.
        // Do NOT assume a localhost DB. The running IntelliJ process may use an OCI DB reachable via Tailscale.
        if (isApply()) {
            if (dataSource == null) {
                log.warn("Refusing to apply reconciliation because DataSource bean is not available in this runtime. Ensure the runner runs inside the authoritative application process.");
                return;
            }
            if ((jdbcUrl == null || jdbcUrl.isBlank()) && (configuredUrl == null || configuredUrl.isBlank())) {
                log.warn("Refusing to apply reconciliation because no datasource URL could be determined (jdbcUrl={}, configuredUrl={}).", jdbcUrl, configuredUrl);
                return;
            }
            // verify a simple test query works against the active datasource before applying
            try (java.sql.Connection conn = dataSource.getConnection(); java.sql.Statement st = conn.createStatement()) {
                java.sql.ResultSet rs = st.executeQuery("SELECT 1");
                if (rs.next()) {
                    log.info("Datasource connectivity test succeeded (metaUrl={})", jdbcUrl);
                } else {
                    log.warn("Datasource connectivity test failed (metaUrl={}). Refusing to apply.", jdbcUrl);
                    return;
                }
            } catch (Exception ex) {
                log.warn("Datasource connectivity test error: {}. Refusing to apply.", ex.getMessage());
                return;
            }
        }

        RestTemplate rt = new RestTemplate();

        // fetch admin token via client_credentials
        String tokenUrl = keycloakProperties.tokenUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloakProperties.adminClientId());
        form.add("client_secret", keycloakProperties.adminClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> tokenReq = new HttpEntity<>(form, headers);

        ResponseEntity<Map> tokenResp = rt.postForEntity(tokenUrl, tokenReq, Map.class);
        if (!tokenResp.getStatusCode().is2xxSuccessful() || tokenResp.getBody() == null || tokenResp.getBody().get("access_token") == null) {
            log.error("Unable to obtain Keycloak admin token: status={} body={}", tokenResp.getStatusCode(), tokenResp.getBody());
            return;
        }
        String adminToken = String.valueOf(tokenResp.getBody().get("access_token"));

        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth(adminToken);

        // list users (page through if necessary) - keep small page size for safety
        int first = 0;
        int max = 100;
        List<Map<String, Object>> allUsers = new ArrayList<>();
        while (true) {
            String usersUrl = keycloakProperties.serverUrl() + "/admin/realms/" + keycloakProperties.realm() + "/users?first=" + first + "&max=" + max;
            HttpEntity<Void> ue = new HttpEntity<>(auth);
            ResponseEntity<List> usersResp = rt.exchange(usersUrl, org.springframework.http.HttpMethod.GET, ue, List.class);
            if (!usersResp.getStatusCode().is2xxSuccessful() || usersResp.getBody() == null) break;
            List<Map<String, Object>> page = usersResp.getBody();
            allUsers.addAll(page);
            if (page.size() < max) break;
            first += max;
        }

        log.info("Found {} Keycloak users to inspect", allUsers.size());

        List<Map<String, Object>> usersMissing = allUsers.stream()
                .filter(u -> {
                    Object attrs = u.get("attributes");
                    if (attrs == null) return true;
                    try {
                        Map<String, Object> map = (Map<String, Object>) attrs;
                        Object v = map.get("legacy_user_id");
                        if (v == null) return true;
                        if (v instanceof Collection) return ((Collection<?>) v).isEmpty();
                        return String.valueOf(v).isBlank();
                    } catch (ClassCastException ex) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

        log.info("Users missing legacy_user_id: {}", usersMissing.size());

        List<Map<String, Object>> suggested = new ArrayList<>();

        for (Map<String, Object> user : usersMissing) {
            String kcId = String.valueOf(user.get("id"));
            String username = String.valueOf(user.get("username"));

            Optional<Customer> byKeycloak = customerRepository.findByKeycloakId(kcId);
            Optional<Customer> byUsername = customerRepository.findByUsername(username);

            Map<String, Object> item = new HashMap<>();
            item.put("keycloakId", kcId);
            item.put("username", username);
            item.put("foundCustomerByKeycloakId", byKeycloak.isPresent());
            item.put("foundCustomerByUsername", byUsername.isPresent());
            if (byKeycloak.isPresent()) item.put("customerId", byKeycloak.get().getId());
            else if (byUsername.isPresent()) item.put("customerId", byUsername.get().getId());

            suggested.add(item);
        }

        // Log a summary table (safe, no secrets)
        log.info("Suggested mappings (only showing users missing legacy_user_id):");
        for (Map<String, Object> s : suggested) {
            log.info("KC id={} username={} -> foundByKC={} foundByUsername={} customerId={}",
                    s.get("keycloakId"), s.get("username"), s.get("foundCustomerByKeycloakId"), s.get("foundCustomerByUsername"), s.get("customerId"));
        }

        if (isApply()) {
            log.info("Apply requested: patching Keycloak users with legacy_user_id where a single authoritative Customer match exists");
            for (Map<String, Object> s : suggested) {
            String kcId = String.valueOf(s.get("keycloakId"));
            String username = String.valueOf(s.get("username"));

            // Re-query to get fresh Customer objects (ensure up-to-date state)
            Optional<Customer> byKeycloak = customerRepository.findByKeycloakId(kcId);
            Optional<Customer> byUsername = customerRepository.findByUsername(username);

            Customer kcCust = byKeycloak.orElse(null);
            Customer userCust = byUsername.orElse(null);

            String decision = null;
            Long customerIdToApply = null;

            if (kcCust != null && userCust != null) {
                if (Objects.equals(kcCust.getId(), userCust.getId())) {
                    decision = "MATCH_CONFIDENT_BY_KEYCLOAK_AND_USERNAME";
                    customerIdToApply = kcCust.getId();
                } else {
                    decision = "SKIPPED_CONFLICT";
                }
            } else if (kcCust != null) {
                decision = "MATCH_CONFIDENT_BY_KEYCLOAK";
                customerIdToApply = kcCust.getId();
            } else if (userCust != null) {
                // ensure applying username match will not overwrite an existing keycloakId
                String existingKc = userCust.getKeycloakId();
                if (existingKc == null || existingKc.isBlank()) {
                    decision = "MATCH_CONFIDENT_BY_USERNAME";
                    customerIdToApply = userCust.getId();
                } else {
                    decision = "SKIPPED_CONFLICT";
                }
            } else {
                decision = "SKIPPED_NO_MATCH";
            }

            if (customerIdToApply != null) {
                // final safety: ensure no other Keycloak user already has this legacy_user_id assigned
                boolean legacyAssignedElsewhere = false;
                for (Map<String, Object> other : allUsers) {
                    Object attrs = other.get("attributes");
                    if (attrs == null) continue;
                    try {
                        Map<String, Object> amap = (Map<String, Object>) attrs;
                        Object v = amap.get("legacy_user_id");
                        if (v == null) continue;
                        if (v instanceof Collection) {
                            for (Object ov : (Collection<?>) v) {
                                if (String.valueOf(ov).equals(String.valueOf(customerIdToApply))) {
                                    // if other.id equals kcId, it's fine (same user)
                                    if (!String.valueOf(other.get("id")).equals(kcId)) {
                                        legacyAssignedElsewhere = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (String.valueOf(v).equals(String.valueOf(customerIdToApply)) && !String.valueOf(other.get("id")).equals(kcId)) {
                                legacyAssignedElsewhere = true;
                            }
                        }
                    } catch (ClassCastException e) {
                        // ignore
                    }
                    if (legacyAssignedElsewhere) break;
                }
                if (legacyAssignedElsewhere) {
                    log.info("Skipping {} for user {} because legacy_user_id {} already assigned to another Keycloak user", decision, username, customerIdToApply);
                    continue;
                }

                // patch user attribute
                String patchUrl = keycloakProperties.serverUrl() + "/admin/realms/" + keycloakProperties.realm() + "/users/" + kcId;
                Map<String, Object> userPayload = new HashMap<>();
                Map<String, Object> attrs = new HashMap<>();
                attrs.put("legacy_user_id", List.of(String.valueOf(customerIdToApply)));
                userPayload.put("attributes", attrs);
                HttpHeaders jsonh = new HttpHeaders();
                jsonh.setBearerAuth(adminToken);
                jsonh.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> patchReq = new HttpEntity<>(userPayload, jsonh);
                rt.put(patchUrl, patchReq);
                log.info("Patched Keycloak user {} with legacy_user_id={} ({})", kcId, customerIdToApply, decision);
            } else {
                log.info("Skipping user {} (decision={})", username, decision);
            }
        }
        log.info("Apply completed");
        } else {
        log.info("Dry-run complete. To apply patches set system property/app setting 'app.reconcile.apply=true' in a controlled environment.");
        }
    }
}
