package eg.gov.healthflow.hfcx.sdk.examples.recipient;

import eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.recipient.BearerTokenValidator;
import eg.gov.healthflow.hfcx.sdk.client.recipient.LocalKeyProvider;
import eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientHandler;
import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full Spring Boot recipient app on a random port and posts
 * a real JWE-encrypted claim. Demonstrates the J5 acceptance criterion
 * "Spring Boot example app boots and accepts a real platform request".
 *
 * <p>Uses {@code java.net.http.HttpClient} directly for HTTP rather than
 * Spring's {@code TestRestTemplate} — the latter's default factory
 * (JDK {@code HttpURLConnection}) chokes on 401 responses to streaming
 * POSTs with {@code HttpRetryException}, which trips up the negative
 * authentication test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {RecipientApplication.class, RecipientApplicationTest.TestConfig.class})
@ActiveProfiles("test")
class RecipientApplicationTest {

    static final RSAPublicKey publicKey;
    static final RSAPrivateKey privateKey;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair pair = kpg.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @LocalServerPort
    int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public LocalKeyProvider testKeyProvider() {
            return () -> privateKey;
        }

        @Bean
        @Primary
        public BearerTokenValidator testBearerValidator() {
            return authHeader -> {
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    throw new AuthenticationException("missing bearer");
                }
            };
        }

        @Bean
        @Primary
        public RecipientHandler testRecipientHandler(
                LocalKeyProvider keyProvider, BearerTokenValidator bearerValidator) {
            return RecipientHandler.builder()
                    .keyProvider(keyProvider)
                    .bearerTokenValidator(bearerValidator)
                    .localParticipantCode("payerco@hcx-egypt")
                    .build();
        }
    }

    private static String validBundle() {
        return "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"http://hcx-egypt.gov.eg/identifiers/national-id\","
                + "\"value\":\"29504150112355\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
    }

    private HttpRequest.Builder requestBuilder(String correlationId) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/claim/submit"))
                .header("Content-Type", "application/json")
                .header(ProtocolHeaders.SENDER_CODE, "myhospital@hcx-egypt")
                .header(ProtocolHeaders.RECIPIENT_CODE, "payerco@hcx-egypt")
                .header(ProtocolHeaders.CORRELATION_ID, correlationId)
                .header(ProtocolHeaders.TIMESTAMP, Instant.now().toString())
                .header(ProtocolHeaders.API_CALL_ID, UUID.randomUUID().toString());
    }

    private String envelope(String payload) {
        OutboundEncryptor encryptor = new OutboundEncryptor(code -> new ParticipantCert(
                code, publicKey, Instant.now().plusSeconds(3600)));
        return "{\"payload\":\"" + encryptor.encrypt(payload, "payerco@hcx-egypt") + "\"}";
    }

    @Test
    void postsAJweEncryptedClaimAndExpects202() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        HttpRequest request = requestBuilder(correlationId)
                .header("Authorization", "Bearer test")
                .POST(HttpRequest.BodyPublishers.ofString(envelope(validBundle())))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(202, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().contains(correlationId),
                "response body must echo the correlation ID");
    }

    @Test
    void missingBearerReturns401() throws Exception {
        // No Authorization header on the request.
        HttpRequest request = requestBuilder(UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(envelope("{\"resourceType\":\"Bundle\",\"type\":\"x\"}")))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }
}
