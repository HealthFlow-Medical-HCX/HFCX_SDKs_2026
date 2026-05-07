package eg.gov.healthflow.hfcx.sdk.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient;
import eg.gov.healthflow.hfcx.sdk.client.request.CheckEligibilityRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.HfcxRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.NotifyPaymentRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SendCommunicationRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitPreauthRequest;
import eg.gov.healthflow.hfcx.sdk.core.HfcxSdkVersion;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.Gateway5xxException;
import eg.gov.healthflow.hfcx.sdk.core.exception.HfcxException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TransportException;
import eg.gov.healthflow.hfcx.sdk.core.exception.UnknownBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
 * <h2>Sprint J4 status</h2>
 *
 * The five sender methods now perform the full outbound flow: registry
 * lookup → JWE encryption → bearer-token authentication → POST to the
 * gateway. Endpoint paths default to those documented in Integration
 * Guide §22 and can be overridden via the builder for non-default
 * deployments.
 *
 * <h2>Errors</h2>
 *
 * <ul>
 *   <li>HTTP 202 from the gateway → returns
 *       {@link HfcxResponse} with {@link Status#ACCEPTED}.</li>
 *   <li>HTTP 401 → {@link AuthenticationException} ({@code ERR-T-002}).</li>
 *   <li>HTTP 4xx with a parseable {@code error.code} body →
 *       typed exception ({@link ProtocolException} / {@link BusinessException}
 *       / {@link TechnicalException}) carrying that code.</li>
 *   <li>HTTP 5xx, network failures: retried up to 3 times with 1s/2s/4s
 *       exponential backoff before raising
 *       {@link TechnicalException} ({@code ERR-T-001}).</li>
 * </ul>
 */
public final class HfcxClient {

    private static final Logger log = LoggerFactory.getLogger(HfcxClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** MDC key used for the correlation ID. Cross-SDK invariant — do not rename. */
    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final List<Duration> DEFAULT_RETRY_DELAYS = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Default §22 endpoint paths. */
    public static final Map<Operation, String> DEFAULT_ENDPOINTS = Map.of(
            Operation.CHECK_ELIGIBILITY, "/v1/coverageeligibility/check",
            Operation.SUBMIT_PREAUTH, "/v1/preauth/submit",
            Operation.SUBMIT_CLAIM, "/v1/claim/submit",
            Operation.SEND_COMMUNICATION, "/v1/communication/on_request",
            Operation.NOTIFY_PAYMENT, "/v1/paymentnotice/notify");

    private final URI gatewayUrl;
    private final String participantCode;
    @SuppressWarnings("unused") // J5 inbound path consumes this; J4 only stores it.
    private final Path privateKeyPath;
    private final KeycloakTokenClient keycloak;
    private final OutboundEncryptor encryptor;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Supplier<String> correlationIdGenerator;
    private final Supplier<String> apiCallIdGenerator;
    private final Map<Operation, String> endpoints;
    private final Duration requestTimeout;
    private final List<Duration> retryDelays;
    private final Sleeper sleeper;

    private HfcxClient(Builder b) {
        this.gatewayUrl = URI.create(Objects.requireNonNull(b.gatewayUrl, "gatewayUrl is required"));
        this.participantCode = Objects.requireNonNull(b.participantCode, "participantCode is required");
        this.privateKeyPath = Objects.requireNonNull(b.privateKeyPath, "privateKeyPath is required");
        this.keycloak = Objects.requireNonNull(b.keycloak, "keycloak is required");
        this.encryptor = Objects.requireNonNull(b.encryptor, "encryptor is required");
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.clock = b.clock != null ? b.clock : Clock.systemUTC();
        this.correlationIdGenerator = b.correlationIdGenerator != null
                ? b.correlationIdGenerator : () -> UUID.randomUUID().toString();
        this.apiCallIdGenerator = b.apiCallIdGenerator != null
                ? b.apiCallIdGenerator : () -> UUID.randomUUID().toString();
        this.endpoints = b.endpoints != null ? Map.copyOf(b.endpoints) : DEFAULT_ENDPOINTS;
        this.requestTimeout = b.requestTimeout != null ? b.requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.retryDelays = b.retryDelays != null ? List.copyOf(b.retryDelays) : DEFAULT_RETRY_DELAYS;
        this.sleeper = b.sleeper != null ? b.sleeper : Thread::sleep;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** SDK version, exposed for {@code User-Agent} construction. */
    public static String sdkVersion() {
        return HfcxSdkVersion.VERSION;
    }

    public HfcxResponse checkEligibility(CheckEligibilityRequest request) {
        return dispatch(Operation.CHECK_ELIGIBILITY, request);
    }

    public HfcxResponse submitPreauth(SubmitPreauthRequest request) {
        return dispatch(Operation.SUBMIT_PREAUTH, request);
    }

    public HfcxResponse submitClaim(SubmitClaimRequest request) {
        return dispatch(Operation.SUBMIT_CLAIM, request);
    }

    public HfcxResponse sendCommunication(SendCommunicationRequest request) {
        return dispatch(Operation.SEND_COMMUNICATION, request);
    }

    public HfcxResponse notifyPayment(NotifyPaymentRequest request) {
        return dispatch(Operation.NOTIFY_PAYMENT, request);
    }

    private HfcxResponse dispatch(Operation operation, HfcxRequest request) {
        Objects.requireNonNull(request, "request");

        String correlationId = request.correlationId() != null
                ? request.correlationId() : correlationIdGenerator.get();
        String apiCallId = apiCallIdGenerator.get();
        URI endpoint = gatewayUrl.resolve(endpoints.get(operation));

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_CORRELATION_ID, correlationId)) {
            log.info("hfcx.{} starting recipientCode={} apiCallId={}",
                    operation, request.recipientCode(), apiCallId);

            // 1. Encrypt the FHIR payload as a JWE compact serialization.
            String jwe = encryptor.encrypt(request.payload(), request.recipientCode());

            // 2. Compose the request envelope.
            String envelope = "{\"payload\":\"" + jwe + "\"}";

            // 3. Build the protocol headers the gateway expects.
            Map<String, String> protoHeaders = ProtocolHeaders.build(
                    participantCode, request.recipientCode(),
                    correlationId, clock.instant(), apiCallId);

            // 4. Authenticate to Keycloak.
            String bearer = keycloak.getToken();

            // 5. POST with retry on 5xx / transport failures.
            HttpResponse<String> response = postWithRetry(endpoint, envelope, bearer, protoHeaders);

            // 6. Map the response.
            return mapResponse(response, correlationId, operation);
        }
    }

    private HttpResponse<String> postWithRetry(
            URI endpoint, String body, String bearer, Map<String, String> protoHeaders) {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .header("User-Agent", "hfcx-sdk-java/" + HfcxSdkVersion.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        protoHeaders.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();

        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 500 && status < 600) {
                    if (attempt < retryDelays.size()) {
                        log.warn("gateway returned HTTP {} (attempt {}/{}); retrying in {}",
                                status, attempt + 1, retryDelays.size() + 1, retryDelays.get(attempt));
                        sleepFor(retryDelays.get(attempt));
                        continue;
                    }
                    throw new Gateway5xxException(
                            "Gateway returned HTTP " + status + " after "
                                    + (retryDelays.size() + 1) + " attempts");
                }
                return response;
            } catch (java.io.IOException e) {
                if (attempt < retryDelays.size()) {
                    log.warn("gateway request failed ({}); retrying in {}",
                            e.getClass().getSimpleName(), retryDelays.get(attempt));
                    sleepFor(retryDelays.get(attempt));
                    continue;
                }
                throw new TransportException(
                        "Gateway request failed after " + (retryDelays.size() + 1) + " attempts", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransportException("Gateway request interrupted", e);
            }
        }
    }

    private HfcxResponse mapResponse(HttpResponse<String> response, String correlationId, Operation operation) {
        int status = response.statusCode();
        if (status == 202) {
            log.info("hfcx.{} accepted by gateway (HTTP 202)", operation);
            return new HfcxResponse(correlationId, Status.ACCEPTED);
        }
        if (status == 401) {
            // Bearer token rejected. The token client cache may be stale —
            // surface as AuthenticationException and let the caller decide
            // whether to re-fetch and retry.
            keycloak.invalidate();
            throw new AuthenticationException(
                    "Gateway rejected bearer token with HTTP 401 for " + operation);
        }
        if (status >= 400 && status < 500) {
            HfcxException ex = parseTypedError(response.body(), status, operation);
            throw ex;
        }
        // 1xx, 2xx other than 202, 3xx redirects.
        throw new TransportException(
                "Gateway returned unexpected HTTP " + status + " for " + operation);
    }

    /**
     * Parse the gateway's error body and map to one of the three SDK
     * exception tiers based on the {@code error.code} prefix.
     */
    private static HfcxException parseTypedError(String body, int status, Operation operation) {
        String code = UnknownBusinessException.CODE;
        String message = "HTTP " + status + " from gateway for " + operation;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = JSON.readTree(body);
                JsonNode err = root.get("error");
                if (err != null) {
                    JsonNode codeNode = err.get("code");
                    if (codeNode != null && !codeNode.isNull()) {
                        code = codeNode.asText();
                    }
                    JsonNode msgNode = err.get("message");
                    if (msgNode != null && !msgNode.isNull()) {
                        message = msgNode.asText();
                    }
                }
            } catch (JsonProcessingException ignored) {
                // Fall through with default code/message.
            }
        }
        // Route the wire code through the catalog so callers see the typed
        // subclass (e.g. NationalIdInvalidException) and can catch by type.
        return HfcxException.fromWireCode(code, message);
    }

    private void sleepFor(Duration d) {
        try {
            sleeper.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(
                    "Interrupted while waiting to retry gateway request", e);
        }
    }

    /** Hook so tests can substitute a no-op sleeper. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Closed set of operations the SDK supports. Each maps to a configurable endpoint path. */
    public enum Operation {
        CHECK_ELIGIBILITY,
        SUBMIT_PREAUTH,
        SUBMIT_CLAIM,
        SEND_COMMUNICATION,
        NOTIFY_PAYMENT
    }

    public static final class Builder {
        private String gatewayUrl;
        private String participantCode;
        private Path privateKeyPath;
        private KeycloakTokenClient keycloak;
        private RegistryClient registryClient;
        private OutboundEncryptor encryptor;
        private HttpClient httpClient;
        private Clock clock;
        private Supplier<String> correlationIdGenerator;
        private Supplier<String> apiCallIdGenerator;
        private Map<Operation, String> endpoints;
        private Duration requestTimeout;
        private List<Duration> retryDelays;
        private Sleeper sleeper;

        public Builder gatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
            return this;
        }

        public Builder participantCode(String participantCode) {
            this.participantCode = participantCode;
            return this;
        }

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

        /**
         * Set the registry client. Internally combined with
         * {@link OutboundEncryptor}; if {@link #encryptor(OutboundEncryptor)}
         * is also called, the encryptor wins.
         */
        public Builder registryClient(RegistryClient registryClient) {
            this.registryClient = registryClient;
            return this;
        }

        /** Override the encryptor (mostly for tests). */
        public Builder encryptor(OutboundEncryptor encryptor) {
            this.encryptor = encryptor;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        Builder correlationIdGenerator(Supplier<String> generator) {
            this.correlationIdGenerator = generator;
            return this;
        }

        Builder apiCallIdGenerator(Supplier<String> generator) {
            this.apiCallIdGenerator = generator;
            return this;
        }

        /** Override individual operation endpoint paths (e.g. for non-default deployments). */
        public Builder endpoints(Map<Operation, String> endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder retryDelays(List<Duration> retryDelays) {
            this.retryDelays = retryDelays;
            return this;
        }

        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public HfcxClient build() {
            if (encryptor == null && registryClient != null) {
                encryptor = new OutboundEncryptor(registryClient);
            }
            return new HfcxClient(this);
        }
    }
}
