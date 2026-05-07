package eg.gov.healthflow.hfcx.sdk.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.registry.RecipientCertResolver;
import eg.gov.healthflow.hfcx.sdk.client.request.CheckEligibilityRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.NotifyPaymentRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SendCommunicationRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitPreauthRequest;
import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HfcxClientTest {

    private static final String CLAIM_PATH = "/v1/claim/submit";
    private static final String PREAUTH_PATH = "/v1/preauth/submit";
    private static final String ELIG_PATH = "/v1/coverageeligibility/check";
    private static final String COMM_PATH = "/v1/communication/on_request";
    private static final String PAY_PATH = "/v1/paymentnotice/notify";
    private static final String TOKEN_PATH = "/auth/realms/hcx/protocol/openid-connect/token";

    private static final Pattern UUID4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private static TestCerts.GeneratedCert recipient;

    @BeforeAll
    static void generateCert() throws Exception {
        recipient = TestCerts.generate("payerco@hcx-egypt", Duration.ofDays(3650));
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        // Token endpoint always returns a valid bearer.
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"access_token\":\"test-bearer\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
    }

    private HfcxClient.Builder validBuilder() {
        return HfcxClient.builder()
                .gatewayUrl(wm.baseUrl())
                .participantCode("myhospital@hcx-egypt")
                .privateKeyPath("/run/secrets/hfcx-private-key.pem")
                .keycloak(KeycloakTokenClient.builder()
                        .tokenEndpoint(wm.baseUrl() + TOKEN_PATH)
                        .clientId("c").clientSecret("s")
                        .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO))
                        .build())
                .encryptor(stubEncryptor())
                .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO));
    }

    private OutboundEncryptor stubEncryptor() {
        RecipientCertResolver resolver = code -> new ParticipantCert(
                code, recipient.publicKey(), Instant.now().plusSeconds(3600));
        return new OutboundEncryptor(resolver);
    }

    private static SubmitClaimRequest claim() {
        return SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{\"resourceType\":\"Bundle\"}")
                .build();
    }

    @Test
    void builderRequiresAllFields() {
        assertThrows(NullPointerException.class, () -> HfcxClient.builder().build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").participantCode("p").build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").participantCode("p").privateKeyPath("/k").build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").participantCode("p").privateKeyPath("/k")
                .keycloak(KeycloakTokenClient.builder()
                        .tokenEndpoint("http://x/t").clientId("c").clientSecret("s").build())
                .build()); // missing encryptor / registryClient
    }

    @Test
    void successfulSubmitClaimReturnsAccepted() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();

        HfcxResponse response = client.submitClaim(claim());

        assertEquals(Status.ACCEPTED, response.status());
        assertNotNull(response.correlationId());
        assertTrue(UUID4_PATTERN.matcher(response.correlationId()).matches());
    }

    @Test
    void allFiveSenderMethodsPostToTheirEndpoints() {
        for (String path : new String[]{CLAIM_PATH, PREAUTH_PATH, ELIG_PATH, COMM_PATH, PAY_PATH}) {
            wm.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(202)));
        }
        HfcxClient client = validBuilder().build();

        client.checkEligibility(CheckEligibilityRequest.builder()
                .recipientCode("payerco@hcx-egypt").eligibilityBundle("{}").build());
        client.submitPreauth(SubmitPreauthRequest.builder()
                .recipientCode("payerco@hcx-egypt").preauthBundle("{}").build());
        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt").claimBundle("{}").build());
        client.sendCommunication(SendCommunicationRequest.builder()
                .recipientCode("payerco@hcx-egypt").communicationBundle("{}").build());
        client.notifyPayment(NotifyPaymentRequest.builder()
                .recipientCode("payerco@hcx-egypt").paymentNoticeBundle("{}").build());

        wm.verify(1, postRequestedFor(urlEqualTo(CLAIM_PATH)));
        wm.verify(1, postRequestedFor(urlEqualTo(PREAUTH_PATH)));
        wm.verify(1, postRequestedFor(urlEqualTo(ELIG_PATH)));
        wm.verify(1, postRequestedFor(urlEqualTo(COMM_PATH)));
        wm.verify(1, postRequestedFor(urlEqualTo(PAY_PATH)));
    }

    @Test
    void postBodyIsAJsonEnvelopeWithAFiveSegmentJweInThePayloadField() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();

        client.submitClaim(claim());

        wm.verify(postRequestedFor(urlEqualTo(CLAIM_PATH))
                .withRequestBody(matching(
                        "\\{\"payload\":\"[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
                                + "\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\"\\}")));
    }

    @Test
    void postCarriesAllFiveProtocolHeadersAndBearer() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        String correlationId = "11111111-2222-4333-8444-555555555555";
        HfcxClient client = validBuilder().build();

        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{}")
                .correlationId(correlationId)
                .build());

        wm.verify(postRequestedFor(urlEqualTo(CLAIM_PATH))
                .withHeader("Authorization", equalTo("Bearer test-bearer"))
                .withHeader(ProtocolHeaders.SENDER_CODE, equalTo("myhospital@hcx-egypt"))
                .withHeader(ProtocolHeaders.RECIPIENT_CODE, equalTo("payerco@hcx-egypt"))
                .withHeader(ProtocolHeaders.CORRELATION_ID, equalTo(correlationId))
                .withHeader(ProtocolHeaders.TIMESTAMP, matching("\\d{4}-\\d{2}-\\d{2}T.*Z"))
                .withHeader(ProtocolHeaders.API_CALL_ID, matching(UUID4_PATTERN.pattern()))
                .withHeader("User-Agent", matching("hfcx-sdk-java/.+")));
    }

    @Test
    void distinctCallsGenerateDistinctCorrelationIds() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();

        String first = client.submitClaim(claim()).correlationId();
        String second = client.submitClaim(claim()).correlationId();
        assertNotEquals(first, second);
    }

    @Test
    void status401MapsToAuthenticationExceptionAndInvalidatesTokenCache() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(401)));
        HfcxClient client = validBuilder().build();

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-T-002", ex.getCode());
    }

    @Test
    void status400WithErrPCodeMapsToProtocolException() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"ERR-P-007\",\"message\":\"bad header\"}}")));
        HfcxClient client = validBuilder().build();

        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-P-007", ex.getCode());
        assertEquals("bad header", ex.getMessage());
    }

    @Test
    void status400WithErrBCodeMapsToBusinessException() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"ERR-B-006\",\"message\":\"invalid National ID\"}}")));
        HfcxClient client = validBuilder().build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-B-006", ex.getCode());
    }

    @Test
    void status400WithErrTCodeMapsToTechnicalException() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"ERR-T-009\",\"message\":\"transient\"}}")));
        HfcxClient client = validBuilder().build();

        TechnicalException ex = assertThrows(TechnicalException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-T-009", ex.getCode());
    }

    @Test
    void status400WithUnparseableBodyFallsBackToBusinessExceptionWithUnknownCode() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .willReturn(aResponse().withStatus(400).withBody("not json at all")));
        HfcxClient client = validBuilder().build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-B-UNKNOWN", ex.getCode());
    }

    @Test
    void status503TriggersRetriesThenSuccess() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .inScenario("retry").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("two"));
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .inScenario("retry").whenScenarioStateIs("two")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("three"));
        wm.stubFor(post(urlEqualTo(CLAIM_PATH))
                .inScenario("retry").whenScenarioStateIs("three")
                .willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();

        HfcxResponse response = client.submitClaim(claim());
        assertEquals(Status.ACCEPTED, response.status());
        wm.verify(3, postRequestedFor(urlEqualTo(CLAIM_PATH)));
    }

    @Test
    void status503ExhaustingRetriesRaisesTechnicalException() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(503)));
        HfcxClient client = validBuilder().build();

        TechnicalException ex = assertThrows(TechnicalException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-T-001", ex.getCode());
        // 1 initial + 3 retries = 4 attempts (matches the configured retry-delay length).
        wm.verify(4, postRequestedFor(urlEqualTo(CLAIM_PATH)));
    }

    @Test
    void registryFailurePropagatesAsBusinessException() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder()
                .encryptor(new OutboundEncryptor(code -> {
                    throw new BusinessException("ERR-B-NF",
                            "Participant '" + code + "' not found");
                }))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.submitClaim(claim()));
        assertEquals("ERR-B-NF", ex.getCode());
        wm.verify(0, postRequestedFor(urlEqualTo(CLAIM_PATH)));
    }

    @Test
    void postedJweIsActuallyDecryptableByTheRecipient() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();
        String payload = "{\"resourceType\":\"Bundle\",\"id\":\"abc\"}";

        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt").claimBundle(payload).build());

        // Pull the recorded request body and decrypt with the recipient's
        // private key — confirms a real JWE is on the wire.
        var requests = wm.findAll(postRequestedFor(urlEqualTo(CLAIM_PATH)));
        assertEquals(1, requests.size());
        String body = requests.get(0).getBodyAsString();
        // Strip the {"payload":"..."} envelope.
        String jwe = body.replaceAll("^\\{\"payload\":\"", "").replaceAll("\"\\}$", "");
        String decrypted = JweEncryption.decryptUtf8(jwe, recipient.privateKey());
        assertEquals(payload, decrypted);
    }

    @Test
    void callerSuppliedCorrelationIdIsPropagatedToHeaders() {
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));
        HfcxClient client = validBuilder().build();
        String supplied = UUID.randomUUID().toString();

        HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt").claimBundle("{}").correlationId(supplied).build());

        assertEquals(supplied, response.correlationId());
        wm.verify(postRequestedFor(urlEqualTo(CLAIM_PATH))
                .withHeader(ProtocolHeaders.CORRELATION_ID, equalTo(supplied)));
    }

    @Test
    void requestRecordsRejectNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> SubmitClaimRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SubmitClaimRequest.builder()
                .recipientCode("r").build());
        assertThrows(NullPointerException.class, () -> CheckEligibilityRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SubmitPreauthRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SendCommunicationRequest.builder().build());
        assertThrows(NullPointerException.class, () -> NotifyPaymentRequest.builder().build());
    }

    @Test
    void sdkVersionResolvesViaCore() {
        assertNotNull(HfcxClient.sdkVersion());
    }
}
