package eg.gov.healthflow.hfcx.sdk.examples.recipient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;
import eg.gov.healthflow.hfcx.sdk.client.HfcxResponse;
import eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor;
import eg.gov.healthflow.hfcx.sdk.client.Status;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.recipient.BearerTokenValidator;
import eg.gov.healthflow.hfcx.sdk.client.recipient.LocalKeyProvider;
import eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientHandler;
import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.request.CheckEligibilityRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.NotifyPaymentRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SendCommunicationRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitPreauthRequest;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.NationalIdInvalidException;
import eg.gov.healthflow.hfcx.sdk.core.exception.NotABundleException;
import eg.gov.healthflow.hfcx.sdk.core.exception.PatientMissingNationalIdException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * In-process equivalent of the platform's §31 cycle suite.
 *
 * <p>Where the platform repo's {@code tests/integration/harness/} runs
 * the four end-to-end cycles (eligibility, preauth, claim, payment
 * notice) against real Docker containers, this test class exercises the
 * full SDK protocol against an in-VM Spring Boot app — same SDK on both
 * sides, no external dependencies. The two halves use different SDK
 * components: {@link HfcxClient} on the sender side,
 * {@link RecipientHandler} on the recipient side.
 *
 * <p>This is deliberately complementary to
 * {@link PlatformMockPayerIntegrationTest}: that test is the canonical
 * cross-component conformance bar; this one is the SDK's hermetic
 * regression net.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {RecipientApplication.class, SdkRoundTripCyclesTest.TestConfig.class})
@ActiveProfiles("test")
class SdkRoundTripCyclesTest {

    private static RSAPublicKey recipientPublic;
    private static RSAPrivateKey recipientPrivate;

    @RegisterExtension
    static WireMockExtension keycloak = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    @LocalServerPort
    int port;

    private HfcxClient client;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();
        recipientPublic = (RSAPublicKey) pair.getPublic();
        recipientPrivate = (RSAPrivateKey) pair.getPrivate();
    }

    @BeforeEach
    void buildClient() {
        keycloak.resetAll();
        keycloak.stubFor(post(urlEqualTo("/auth/realms/hcx/protocol/openid-connect/token"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"access_token\":\"cycle-test\",\"expires_in\":300,"
                                + "\"token_type\":\"Bearer\"}")));

        client = HfcxClient.builder()
                .gatewayUrl("http://localhost:" + port)
                .participantCode("myhospital@hcx-egypt")
                .privateKeyPath("/run/secrets/hfcx-private-key.pem")
                .keycloak(KeycloakTokenClient.builder()
                        .tokenEndpoint(keycloak.baseUrl()
                                + "/auth/realms/hcx/protocol/openid-connect/token")
                        .clientId("c").clientSecret("s")
                        .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO))
                        .build())
                .encryptor(new OutboundEncryptor(code -> new ParticipantCert(
                        code, recipientPublic, Instant.now().plusSeconds(3600))))
                .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO))
                .build();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        LocalKeyProvider testKeyProvider() {
            return () -> recipientPrivate;
        }

        @Bean
        @Primary
        BearerTokenValidator testBearerValidator() {
            return authHeader -> {
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    throw new AuthenticationException("missing bearer");
                }
            };
        }

        @Bean
        @Primary
        RecipientHandler testRecipientHandler(
                LocalKeyProvider keyProvider, BearerTokenValidator bearerValidator) {
            return RecipientHandler.builder()
                    .keyProvider(keyProvider)
                    .bearerTokenValidator(bearerValidator)
                    .localParticipantCode("payerco@hcx-egypt")
                    .build();
        }
    }

    private static String validBundle() {
        return "{\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"http://hcx-egypt.gov.eg/identifiers/national-id\","
                + "\"value\":\"29504150112355\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
    }

    // ─────────────────────────────────────────────────────────────────
    // Positive §31 cycles — eligibility / preauth / claim / payment /
    // communication. All five must round-trip cleanly.
    // ─────────────────────────────────────────────────────────────────

    @Test
    void eligibilityCycleAccepted() {
        HfcxResponse response = client.checkEligibility(CheckEligibilityRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .eligibilityBundle(validBundle())
                .build());
        assertEquals(Status.ACCEPTED, response.status());
        assertNotNull(response.correlationId());
    }

    @Test
    void preauthCycleAccepted() {
        HfcxResponse response = client.submitPreauth(SubmitPreauthRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .preauthBundle(validBundle())
                .build());
        assertEquals(Status.ACCEPTED, response.status());
    }

    @Test
    void claimCycleAccepted() {
        HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle(validBundle())
                .build());
        assertEquals(Status.ACCEPTED, response.status());
    }

    @Test
    void communicationCycleAccepted() {
        HfcxResponse response = client.sendCommunication(SendCommunicationRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .communicationBundle(validBundle())
                .build());
        assertEquals(Status.ACCEPTED, response.status());
    }

    @Test
    void paymentNoticeCycleAccepted() {
        HfcxResponse response = client.notifyPayment(NotifyPaymentRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .paymentNoticeBundle(validBundle())
                .build());
        assertEquals(Status.ACCEPTED, response.status());
    }

    // ─────────────────────────────────────────────────────────────────
    // Negative cycles — recipient-layer rejections must propagate back
    // to the sender as the appropriate typed exception.
    // ─────────────────────────────────────────────────────────────────

    @Test
    void claimWithMalformedPatientReturnsTypedFhirException() {
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"http://other.example.com/id\",\"value\":\"x\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        PatientMissingNationalIdException ex = assertThrows(
                PatientMissingNationalIdException.class,
                () -> client.submitClaim(SubmitClaimRequest.builder()
                        .recipientCode("payerco@hcx-egypt")
                        .claimBundle(bundle)
                        .build()));
        assertEquals(PatientMissingNationalIdException.CODE, ex.getCode());
    }

    @Test
    void claimWithInvalidNationalIdValueReturnsTypedEgyptianException() {
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"http://hcx-egypt.gov.eg/identifiers/national-id\","
                + "\"value\":\"00000000000000\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        NationalIdInvalidException ex = assertThrows(
                NationalIdInvalidException.class,
                () -> client.submitClaim(SubmitClaimRequest.builder()
                        .recipientCode("payerco@hcx-egypt")
                        .claimBundle(bundle)
                        .build()));
        assertEquals(NationalIdInvalidException.CODE, ex.getCode());
    }

    @Test
    void claimWithNonBundlePayloadReturnsTypedFhirException() {
        NotABundleException ex = assertThrows(
                NotABundleException.class,
                () -> client.submitClaim(SubmitClaimRequest.builder()
                        .recipientCode("payerco@hcx-egypt")
                        .claimBundle("{\"resourceType\":\"Patient\"}")
                        .build()));
        assertEquals(NotABundleException.CODE, ex.getCode());
    }
}
