package com.claimassist.platform.api_gateway.security;

import com.claimassist.platform.api_gateway.config.GatewaySecurityConfig;
import com.claimassist.platform.api_gateway.properties.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.FilteringWebHandler;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the REAL {@link GatewaySecurityConfig#securityWebFilterChain} against a local
 * RSA key (no Keycloak/Docker required): the edge OAuth2 Resource Server must
 * <ul>
 *   <li>permit the configured public routes without any token,</li>
 *   <li>reject a missing / malformed / invalid / expired / wrong-issuer token on protected
 *       routes with 401,</li>
 *   <li>allow a valid user or service token through (authentication at the edge),</li>
 *   <li>NOT over-authorize by role (downstream services enforce their own authorization).</li>
 * </ul>
 */
class GatewaySecurityConfigTest {

    private static KeyPair KEY_PAIR;
    private static KeyPair OTHER_KEY_PAIR;
    private static String ISSUER = "http://localhost:8180/realms/claimassist";
    private static String AUDIENCE = "account";

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KEY_PAIR = kpg.generateKeyPair();
        OTHER_KEY_PAIR = kpg.generateKeyPair();
    }

    private SecurityWebFilterChain buildChain(String... publicRoutes) throws Exception {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("reactiveJwtDecoder", ReactiveJwtDecoder.class, () -> {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        });
        ctx.refresh();

        ServerHttpSecurity http = ServerHttpSecurity.http();
        Method setContext = ServerHttpSecurity.class
                .getDeclaredMethod("setApplicationContext", ApplicationContext.class);
        setContext.setAccessible(true);
        setContext.invoke(http, ctx);

        SecurityProperties props = new SecurityProperties(List.of(publicRoutes));
        GatewaySecurityConfig config = new GatewaySecurityConfig(props, new ObjectMapper());
        return config.securityWebFilterChain(http);
    }

    private WebTestClient client(SecurityWebFilterChain chain) {
        WebFilterChainProxy proxy = new WebFilterChainProxy(List.of(chain));
        WebHandler handler = new FilteringWebHandler(this::writeOk, List.of((WebFilter) proxy));
        return WebTestClient.bindToWebHandler(handler).build();
    }

    private Mono<Void> writeOk(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap("{}".getBytes())));
    }

    private String tokenFor(KeyPair signer, String issuer, Instant issuedAt, Instant expiresAt) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) signer.getPublic())
                .privateKey(signer.getPrivate()).keyID("test-kid").build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        JwtEncoder encoder = new NimbusJwtEncoder(jwks);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-kid").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(AUDIENCE))
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("realm_access", Map.of("roles", List.of("user")))
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, claims));
        return jwt.getTokenValue();
    }

    private String validToken() {
        return tokenFor(KEY_PAIR, ISSUER, Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
    }

    @Test
    void publicRouteIsAccessibleWithoutToken() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup", "/actuator/health");
        client(chain).get().uri("/auth/signup").exchange()
                .expectStatus().isOk();
    }

    @Test
    void healthProbeIsAccessibleWithoutToken() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup", "/actuator/health", "/actuator/health/**");
        client(chain).get().uri("/actuator/health").exchange()
                .expectStatus().isOk();
        client(chain).get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRouteRequiresToken() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        String token = tokenFor(OTHER_KEY_PAIR, ISSUER, Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = tokenFor(KEY_PAIR, ISSUER, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String token = tokenFor(KEY_PAIR, "https://evil.example.com/realms/other",
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenAllowsProtectedRoute() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer " + validToken())
                .exchange().expectStatus().isOk();
    }

    @Test
    void gatewayDoesNotOverAuthorizeByRole() throws Exception {
        // The gateway authenticates at the edge but deliberately does NOT authorize by
        // role (downstream services enforce their own authorization). An authenticated
        // user token passes through - it is the downstream @PreAuthorize that decides.
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        String token = tokenFor(KEY_PAIR, ISSUER, Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
        client(chain).get().uri("/claims/1").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk();
        assertThat(token).isNotBlank();
    }

    @Test
    void unauthorizedReturnsJsonBody() throws Exception {
        SecurityWebFilterChain chain = buildChain("/auth/signup");
        byte[] body = client(chain).get().uri("/claims/1")
                .exchange().expectStatus().isUnauthorized()
                .expectBody().returnResult().getResponseBody();
        assertThat(new String(body)).contains("Missing or invalid bearer token");
    }
}