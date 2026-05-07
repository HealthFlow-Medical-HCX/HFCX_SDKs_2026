package eg.gov.healthflow.hfcx.sdk.client;

import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.request.CheckEligibilityRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.HfcxRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.NotifyPaymentRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SendCommunicationRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitPreauthRequest;
import eg.gov.healthflow.hfcx.sdk.core.HfcxSdkVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * High-level entry point for HFCX participants acting as senders.
 *
 * <p>Construct via {@link #builder()}; the builder fail-fasts at
 * {@link Builder#build()} on missing required fields.
 *
 * <h2>Sprint J3 status</h2>
 *
 * The five sender methods construct the protocol headers (per Integration
 * Guide §24.5), generate or propagate a correlation ID (UUID4 if the
 * caller passed {@code null}), and emit SLF4J log lines with the
 * correlation ID in MDC. They do <strong>not</strong> yet POST to the
 * gateway — Sprint J4 wires the registry lookup, JWE encryption, and
 * outbound HTTP path. Each method currently returns an
 * {@link HfcxResponse} with {@link Status#STUBBED}.
 */
public final class HfcxClient {

    private static final Logger log = LoggerFactory.getLogger(HfcxClient.class);

    /** MDC key used for the correlation ID. Cross-SDK invariant — do not rename. */
    public static final String MDC_CORRELATION_ID = "correlationId";

    private final String gatewayUrl;
    private final String participantCode;
    private final Path privateKeyPath;
    private final KeycloakTokenClient keycloak;
    private final Clock clock;
    private final Supplier<String> correlationIdGenerator;
    private final Supplier<String> apiCallIdGenerator;

    private HfcxClient(Builder b) {
        this.gatewayUrl = Objects.requireNonNull(b.gatewayUrl, "gatewayUrl is required");
        this.participantCode = Objects.requireNonNull(b.participantCode, "participantCode is required");
        this.privateKeyPath = Objects.requireNonNull(b.privateKeyPath, "privateKeyPath is required");
        this.keycloak = Objects.requireNonNull(b.keycloak, "keycloak is required");
        this.clock = b.clock != null ? b.clock : Clock.systemUTC();
        this.correlationIdGenerator = b.correlationIdGenerator != null
                ? b.correlationIdGenerator : () -> UUID.randomUUID().toString();
        this.apiCallIdGenerator = b.apiCallIdGenerator != null
                ? b.apiCallIdGenerator : () -> UUID.randomUUID().toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** SDK version, exposed for {@code User-Agent} construction in Sprint J4. */
    public static String sdkVersion() {
        return HfcxSdkVersion.VERSION;
    }

    public HfcxResponse checkEligibility(CheckEligibilityRequest request) {
        return dispatch("checkEligibility", request);
    }

    public HfcxResponse submitPreauth(SubmitPreauthRequest request) {
        return dispatch("submitPreauth", request);
    }

    public HfcxResponse submitClaim(SubmitClaimRequest request) {
        return dispatch("submitClaim", request);
    }

    public HfcxResponse sendCommunication(SendCommunicationRequest request) {
        return dispatch("sendCommunication", request);
    }

    public HfcxResponse notifyPayment(NotifyPaymentRequest request) {
        return dispatch("notifyPayment", request);
    }

    /**
     * Shared dispatch path — generates IDs, constructs headers, propagates
     * the correlation ID via MDC, and (in Sprint J4) will perform the
     * registry lookup + JWE encryption + HTTP POST.
     */
    private HfcxResponse dispatch(String operation, HfcxRequest request) {
        Objects.requireNonNull(request, "request");

        String correlationId = request.correlationId() != null
                ? request.correlationId()
                : correlationIdGenerator.get();
        String apiCallId = apiCallIdGenerator.get();

        // Try-with-resources removes the MDC key on every exit path,
        // including exceptional ones — callers cannot end up with a
        // stale correlation ID leaking into unrelated log lines.
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_CORRELATION_ID, correlationId)) {
            log.info("hfcx.{} starting recipientCode={} apiCallId={}",
                    operation, request.recipientCode(), apiCallId);

            Map<String, String> headers = ProtocolHeaders.build(
                    participantCode,
                    request.recipientCode(),
                    correlationId,
                    clock.instant(),
                    apiCallId);

            log.debug("hfcx.{} constructed protocol headers: {}", operation, headers);

            // Sprint J4 lands the registry-lookup → encrypt → POST path here.
            // For J3 we surface the constructed headers via logs and return
            // a STUBBED response so callers can wire correlation-ID
            // tracking ahead of the outbound HTTP path landing.
            log.warn("hfcx.{} stubbed — outbound HTTP path lands in Sprint J4", operation);

            return new HfcxResponse(correlationId, Status.STUBBED);
        }
    }

    public static final class Builder {
        private String gatewayUrl;
        private String participantCode;
        private Path privateKeyPath;
        private KeycloakTokenClient keycloak;
        private Clock clock;
        private Supplier<String> correlationIdGenerator;
        private Supplier<String> apiCallIdGenerator;

        public Builder gatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
            return this;
        }

        public Builder participantCode(String participantCode) {
            this.participantCode = participantCode;
            return this;
        }

        /** Accepts a string path; {@link #privateKeyPath(Path)} accepts a {@link Path} directly. */
        public Builder privateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath != null ? Path.of(privateKeyPath) : null;
            return this;
        }

        public Builder privateKeyPath(Path privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public Builder keycloak(KeycloakTokenClient keycloak) {
            this.keycloak = keycloak;
            return this;
        }

        /** Override the clock used to stamp outbound request timestamps. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** Internal — tests inject a deterministic generator to make MDC assertions reproducible. */
        Builder correlationIdGenerator(Supplier<String> generator) {
            this.correlationIdGenerator = generator;
            return this;
        }

        /** Internal — tests inject a deterministic generator. */
        Builder apiCallIdGenerator(Supplier<String> generator) {
            this.apiCallIdGenerator = generator;
            return this;
        }

        public HfcxClient build() {
            return new HfcxClient(this);
        }
    }
}
