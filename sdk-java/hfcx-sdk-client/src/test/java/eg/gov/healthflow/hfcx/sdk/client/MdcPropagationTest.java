package eg.gov.healthflow.hfcx.sdk.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.registry.RecipientCertResolver;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every log line emitted during a transaction carries the
 * correlation ID in MDC under the {@link HfcxClient#MDC_CORRELATION_ID}
 * key, and that MDC is cleaned up on every exit path so a stale ID
 * cannot leak into unrelated work on the same thread.
 */
class MdcPropagationTest {

    private static final String CLAIM_PATH = "/v1/claim/submit";
    private static final String TOKEN_PATH = "/auth/realms/hcx/protocol/openid-connect/token";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private static TestCerts.GeneratedCert recipient;
    private ListAppender<ILoggingEvent> appender;
    private Logger sdkLogger;

    @BeforeAll
    static void generateCert() throws Exception {
        recipient = TestCerts.generate("payerco@hcx-egypt", Duration.ofDays(3650));
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        wm.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(200).withBody(
                "{\"access_token\":\"test-bearer\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wm.stubFor(post(urlEqualTo(CLAIM_PATH)).willReturn(aResponse().withStatus(202)));

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        sdkLogger = ctx.getLogger(HfcxClient.class);
        appender = new ListAppender<>();
        appender.setContext(ctx);
        appender.start();
        sdkLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        sdkLogger.detachAppender(appender);
        MDC.clear();
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

    @Test
    void everyLogLineForATransactionCarriesTheCorrelationId() {
        String correlationId = "11111111-2222-4333-8444-555555555555";
        HfcxClient client = validBuilder().build();

        HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{}")
                .correlationId(correlationId)
                .build());

        assertEquals(correlationId, response.correlationId());

        List<ILoggingEvent> events = appender.list;
        assertFalse(events.isEmpty(), "submitClaim must emit at least one log event");
        for (ILoggingEvent event : events) {
            assertEquals(correlationId,
                    event.getMDCPropertyMap().get(HfcxClient.MDC_CORRELATION_ID),
                    "log event '" + event.getFormattedMessage()
                            + "' is missing the correlation ID in MDC");
        }
    }

    @Test
    void mdcIsClearedAfterTheCallReturns() {
        HfcxClient client = validBuilder().build();
        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("r").claimBundle("{}").correlationId("abc").build());

        assertNull(MDC.get(HfcxClient.MDC_CORRELATION_ID),
                "MDC must be cleaned up after dispatch returns");
    }

    @Test
    void autoGeneratedCorrelationIdAlsoAppearsInMdc() {
        HfcxClient client = validBuilder()
                .correlationIdGenerator(() -> "deterministic-test-id")
                .build();

        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("r").claimBundle("{}").build());

        for (ILoggingEvent event : appender.list) {
            assertEquals("deterministic-test-id",
                    event.getMDCPropertyMap().get(HfcxClient.MDC_CORRELATION_ID));
        }
    }

    @Test
    void distinctTransactionsCarryDistinctCorrelationIdsInMdc() {
        HfcxClient client = validBuilder().build();

        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("r").claimBundle("{}").correlationId("first").build());
        int countAfterFirst = appender.list.size();
        client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("r").claimBundle("{}").correlationId("second").build());

        assertTrue(appender.list.size() > countAfterFirst);
        for (int i = 0; i < countAfterFirst; i++) {
            assertEquals("first",
                    appender.list.get(i).getMDCPropertyMap().get(HfcxClient.MDC_CORRELATION_ID));
        }
        for (int i = countAfterFirst; i < appender.list.size(); i++) {
            assertEquals("second",
                    appender.list.get(i).getMDCPropertyMap().get(HfcxClient.MDC_CORRELATION_ID));
        }
    }
}
