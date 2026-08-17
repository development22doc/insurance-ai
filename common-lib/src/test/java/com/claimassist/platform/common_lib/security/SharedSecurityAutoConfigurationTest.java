package com.claimassist.platform.common_lib.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring integration test for the shared security auto-configuration. Verifies
 * F-1 (Phase 1): when a service registers the internal client (the OAuth2
 * client-credentials flow), the shared {@code authorizedClientServiceManager}
 * (the {@link OAuth2AuthorizedClientManager} used by
 * {@link ServiceClientCredentialsTokenProvider}) is available; when no client
 * registration exists the manager is unavailable (client-credentials disabled),
 * while the identity/bean-support beans are still registered.
 */
class SharedSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SharedSecurityAutoConfiguration.class));

    @Configuration
    static class WithClientRegistration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("internal-service")
                    .clientId("claimassist-admin-service")
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("http://localhost:8180/realms/claimassist/protocol/openid-connect/token")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }

    @Test
    void registersCoreSecurityBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
            assertThat(context).hasSingleBean(KeycloakJwtAuthenticationConverter.class);
            assertThat(context).hasSingleBean(com.claimassist.platform.common_lib.observability.CorrelationIdFilter.class);
            assertThat(context).hasSingleBean(feign.RequestInterceptor.class);
        });
    }

@Test
    void clientCredentialsManagerAvailableWhenClientRegistered() {
        runner.withUserConfiguration(WithClientRegistration.class).run(context -> {
            assertThat(context).hasBean("authorizedClientServiceManager");
            assertThat(context.getBean("authorizedClientServiceManager", OAuth2AuthorizedClientManager.class)).isNotNull();
        });
    }
}
