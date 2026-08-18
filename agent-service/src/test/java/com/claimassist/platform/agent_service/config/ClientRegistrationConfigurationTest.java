package com.claimassist.platform.agent_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRegistrationConfigurationTest {

    @Test
    void buildsClientCredentialsRegistration() {
        OAuth2ClientConfig.OAuth2ClientProperties props = new OAuth2ClientConfig.OAuth2ClientProperties();
        props.setRegistrationId("internal-service");
        props.setClientId("client-id");
        props.setClientSecret("secret");
        props.setTokenUri("http://idp/token");

        ClientRegistrationConfiguration config = new ClientRegistrationConfiguration(props);
        ClientRegistrationRepository repo = config.clientRegistrationRepository();

        assertThat(repo).isInstanceOf(InMemoryClientRegistrationRepository.class);
        ClientRegistration registration = repo.findByRegistrationId("internal-service");
        assertThat(registration).isNotNull();
        assertThat(registration.getClientId()).isEqualTo("client-id");
        assertThat(registration.getClientSecret()).isEqualTo("secret");
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("http://idp/token");
        assertThat(registration.getAuthorizationGrantType().getValue()).isEqualTo("client_credentials");
    }

    @Test
    void oauth2ClientPropertiesDefaults() {
        OAuth2ClientConfig.OAuth2ClientProperties props = new OAuth2ClientConfig.OAuth2ClientProperties();
        assertThat(props.getRegistrationId()).isEqualTo("internal-service");
    }
}