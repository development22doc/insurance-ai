package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Phase 3, Section 15: external-dependency resilience for Keycloak provisioning.
 *
 * <p>The provisioning service uses the shared {@link RestClient} (bounded
 * timeouts from Phase 2) and maps ANY transport failure to
 * {@link ServiceUnavailableException}. Crucially it does NOT blindly retry a
 * mutating provisioning call (which could create a duplicate side effect /
 * orphan), and it does NOT hide the failure - the caller's compensation logic
 * decides how to reconcile. This test pins that classification and
 * non-retry behavior, and covers the create/update/delete idempotency paths.
 */
class KeycloakUserProvisioningServiceTest {

    private RestClient restClient;
    private MockRestServiceServer server;
    private KeycloakUserProvisioningService service;
    private KeycloakProperties props;

    private static final String TOKEN_JSON = "{\"access_token\":\"admin-token\"}";

    @BeforeEach
    void setUp() {
        props = new KeycloakProperties(
                "http://keycloak:8180", "claimassist",
                "app-client", "secret", "http://redirect",
                "admin-client", "admin-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        service = new KeycloakUserProvisioningService(
                props, restClient, mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    private void expectAdminToken() {
        server.expect(requestTo(props.tokenUri()))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(TOKEN_JSON));
    }

    @Test
    void transientFailureOnAdminTokenFetchIsClassifiedAndNotRetried() {
        server.expect(requestTo(props.tokenUri()))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.createUser("a@b.com", "Alice", "pw", 1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void createUser_success_returnsKeycloakUserId() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri()))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .header(HttpHeaders.LOCATION,
                                "http://keycloak:8180/admin/realms/claimassist/users/kc-123"));

        String id = service.createUser("a@b.com", "Alice", "pw", 1L);

        assertThat(id).isEqualTo("kc-123");
        server.verify();
    }

    @Test
    void createUser_noLocationHeader_throwsServiceUnavailable() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> service.createUser("a@b.com", "Alice", "pw", 1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("no Location header");
        server.verify();
    }

    @Test
    void createUser_adminTokenMissing_throwsServiceUnavailable() {
        server.expect(requestTo(props.tokenUri()))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        assertThatThrownBy(() -> service.createUser("a@b.com", "Alice", "pw", 1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("admin access token");
    }

    @Test
    void createUser_transportFailureAfterToken_throwsServiceUnavailable() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri()))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.createUser("a@b.com", "Alice", "pw", 1L))
                .isInstanceOf(ServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void deleteUser_blankId_doesNothing() {
        assertThatCode(() -> service.deleteUser("  ")).doesNotThrowAnyException();
        assertThatCode(() -> service.deleteUser(null)).doesNotThrowAnyException();
    }

    @Test
    void deleteUser_success() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri() + "/kc-123"))
                .andExpect(method(DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatCode(() -> service.deleteUser("kc-123")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void deleteUser_404_treatedAsIdempotentSuccess() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri() + "/gone"))
                .andExpect(method(DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> service.deleteUser("gone")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void deleteUser_otherError_throwsServiceUnavailable() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri() + "/kc-123"))
                .andExpect(method(DELETE))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.deleteUser("kc-123"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("compensation failed");
        server.verify();
    }

    @Test
    void updateUser_blankId_doesNothing() {
        assertThatCode(() -> service.updateUser("  ", "New", "u@e.com")).doesNotThrowAnyException();
        assertThatCode(() -> service.updateUser(null, "New", "u@e.com")).doesNotThrowAnyException();
    }

    @Test
    void updateUser_success() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri() + "/kc-123"))
                .andExpect(method(PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatCode(() -> service.updateUser("kc-123", "Alice Smith", "a@b.com")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void updateUser_transportFailure_throwsServiceUnavailable() {
        expectAdminToken();
        server.expect(requestTo(props.adminUsersUri() + "/kc-123"))
                .andExpect(method(PUT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.updateUser("kc-123", "Alice", "a@b.com"))
                .isInstanceOf(ServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void buildAdminTokenRequestForm_setsGrantTypeAndCredentials() {
        MultiValueMap<String, String> form = service.buildAdminTokenRequestForm();

        assertThat(form.getFirst("grant_type")).isEqualTo("client_credentials");
        assertThat(form.getFirst("client_id")).isEqualTo("admin-client");
        assertThat(form.getFirst("client_secret")).isEqualTo("admin-secret");
    }
}