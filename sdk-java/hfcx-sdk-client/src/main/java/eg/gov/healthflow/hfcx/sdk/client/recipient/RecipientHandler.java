package eg.gov.healthflow.hfcx.sdk.client.recipient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.core.exception.EnvelopeMalformedJsonException;
import eg.gov.healthflow.hfcx.sdk.core.exception.EnvelopeMissingPayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Inbound counterpart of {@link eg.gov.healthflow.hfcx.sdk.client.HfcxClient}.
 *
 * <p>Participants running their own HCX-API instance wire this into their
 * web framework's request handler:
 *
 * <ol>
 *   <li>Extract the {@code Authorization} header and the five
 *       {@code x-hcx-*} protocol headers.</li>
 *   <li>Parse the request body to extract the JWE compact serialization
 *       from the {@code payload} field.</li>
 *   <li>Call {@link #handle(String, Map, String)} with those three pieces.</li>
 *   <li>On success, return HTTP 202 with the correlation ID; on
 *       any thrown {@code HfcxException}, map to the appropriate 4xx
 *       error response per the framework adapter's policy.</li>
 * </ol>
 *
 * <p>Each of the four {@link Layer}s — {@code BEARER}, {@code HEADERS},
 * {@code FHIR}, {@code EGYPTIAN} — can be enabled or disabled
 * independently. Default for every layer is enabled. Disabling
 * {@code BEARER} requires no validator; disabling {@code FHIR} or
 * {@code EGYPTIAN} skips their respective walks; disabling
 * {@code HEADERS} skips header verification but still requires the five
 * headers to be supplied (the correlation ID is propagated to MDC and
 * returned in the result).
 */
public final class RecipientHandler {

    private static final Logger log = LoggerFactory.getLogger(RecipientHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InboundDecryptor decryptor;
    private final HeaderValidator headerValidator;
    private final FhirValidator fhirValidator;
    private final EgyptianBundleValidator egyptianValidator;
    private final BearerTokenValidator bearerValidator;
    private final Set<Layer> enabledLayers;

    private RecipientHandler(Builder b) {
        this.decryptor = Objects.requireNonNull(b.decryptor, "decryptor is required");
        this.enabledLayers = b.enabledLayers != null
                ? EnumSet.copyOf(b.enabledLayers) : EnumSet.allOf(Layer.class);
        this.headerValidator = enabledLayers.contains(Layer.HEADERS)
                ? new HeaderValidator(
                        Objects.requireNonNull(b.localParticipantCode,
                                "localParticipantCode is required when HEADERS layer is enabled"),
                        b.clock != null ? b.clock : Clock.systemUTC(),
                        b.timestampTolerance != null
                                ? b.timestampTolerance : HeaderValidator.DEFAULT_TIMESTAMP_TOLERANCE)
                : null;
        this.fhirValidator = enabledLayers.contains(Layer.FHIR) ? new FhirValidator() : null;
        this.egyptianValidator = enabledLayers.contains(Layer.EGYPTIAN)
                ? new EgyptianBundleValidator() : null;
        this.bearerValidator = b.bearerValidator;
        if (enabledLayers.contains(Layer.BEARER) && bearerValidator == null) {
            throw new IllegalStateException(
                    "Layer.BEARER is enabled but no BearerTokenValidator was supplied. "
                            + "The SDK does NOT ship a default trust-everything validator; "
                            + "configure one or disable Layer.BEARER explicitly.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param authorizationHeader the full {@code Authorization} request
     *     header; may be null when {@link Layer#BEARER} is disabled.
     * @param protocolHeaders the five {@code x-hcx-*} headers — required
     *     even when {@link Layer#HEADERS} is disabled because we still
     *     need the correlation ID for logging/MDC.
     * @param requestBody the raw HTTP request body (a JSON envelope of
     *     the form {@code {"payload": "<jwe>"}}).
     * @return the decrypted Bundle JSON plus the validated headers.
     */
    public RecipientResult handle(
            String authorizationHeader,
            Map<String, String> protocolHeaders,
            String requestBody) {
        Objects.requireNonNull(protocolHeaders, "protocolHeaders");
        Objects.requireNonNull(requestBody, "requestBody");

        String correlationId = protocolHeaders.getOrDefault(
                ProtocolHeaders.CORRELATION_ID, "no-correlation-id");

        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                HfcxClient.MDC_CORRELATION_ID, correlationId)) {
            log.info("recipient: handling inbound request with {} layers enabled", enabledLayers);

            // 1. Bearer token (cheapest hard rejection).
            if (enabledLayers.contains(Layer.BEARER)) {
                bearerValidator.validate(authorizationHeader);
            }

            // 2. Protocol headers.
            if (enabledLayers.contains(Layer.HEADERS)) {
                headerValidator.validate(protocolHeaders);
            }

            // 3. Decrypt JWE.
            String jwe = extractPayload(requestBody);
            String decrypted = decryptor.decrypt(jwe);

            // 4. FHIR profile validation.
            if (enabledLayers.contains(Layer.FHIR)) {
                fhirValidator.validate(decrypted);
            }

            // 5. Egyptian field validation.
            if (enabledLayers.contains(Layer.EGYPTIAN)) {
                egyptianValidator.validate(decrypted);
            }

            log.info("recipient: accepted (all enabled layers passed)");
            return new RecipientResult(decrypted, protocolHeaders, correlationId);
        }
    }

    /** Diagnostic — which layers will run on the next call. */
    public Set<Layer> enabledLayers() {
        return EnumSet.copyOf(enabledLayers);
    }

    private static String extractPayload(String requestBody) {
        try {
            JsonNode root = JSON.readTree(requestBody);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull() || payload.asText().isEmpty()) {
                throw new EnvelopeMissingPayloadException(
                        "Request body envelope missing required 'payload' field");
            }
            return payload.asText();
        } catch (JsonProcessingException e) {
            throw new EnvelopeMalformedJsonException(
                    "Request body is not valid JSON: " + e.getMessage());
        }
    }

    public static final class Builder {
        private InboundDecryptor decryptor;
        private LocalKeyProvider keyProvider;
        private String localParticipantCode;
        private Map<Layer, Boolean> layerToggles = new EnumMap<>(Layer.class);
        private EnumSet<Layer> enabledLayers;
        private Clock clock;
        private Duration timestampTolerance;
        private BearerTokenValidator bearerValidator;

        public Builder decryptor(InboundDecryptor decryptor) {
            this.decryptor = decryptor;
            return this;
        }

        /** Convenience: builds an {@link InboundDecryptor} from the key provider. */
        public Builder keyProvider(LocalKeyProvider keyProvider) {
            this.keyProvider = keyProvider;
            return this;
        }

        public Builder localParticipantCode(String localParticipantCode) {
            this.localParticipantCode = localParticipantCode;
            return this;
        }

        public Builder bearerTokenValidator(BearerTokenValidator validator) {
            this.bearerValidator = validator;
            return this;
        }

        /** Toggle a single layer. Default for every layer is enabled. */
        public Builder enable(Layer layer, boolean enabled) {
            this.layerToggles.put(layer, enabled);
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder timestampTolerance(Duration timestampTolerance) {
            this.timestampTolerance = timestampTolerance;
            return this;
        }

        public RecipientHandler build() {
            if (decryptor == null && keyProvider != null) {
                decryptor = new InboundDecryptor(keyProvider);
            }
            EnumSet<Layer> enabled = EnumSet.allOf(Layer.class);
            for (Map.Entry<Layer, Boolean> e : layerToggles.entrySet()) {
                if (Boolean.FALSE.equals(e.getValue())) {
                    enabled.remove(e.getKey());
                }
            }
            this.enabledLayers = enabled;
            return new RecipientHandler(this);
        }
    }
}
