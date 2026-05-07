package eg.gov.healthflow.hfcx.sdk.client.recipient;

import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a successful {@code RecipientHandler.handle(...)} call.
 *
 * <p>If any layer rejects the request, the handler throws an
 * {@code HfcxException} subtype instead of returning. Callers translate
 * that into the appropriate HTTP error response in their own framework
 * adapter (Spring Boot, Micronaut, etc.).
 *
 * @param decryptedPayload the FHIR Bundle JSON, recovered from the JWE
 * @param protocolHeaders  the validated protocol headers as received
 * @param correlationId    convenience accessor; equal to
 *     {@code protocolHeaders.get(ProtocolHeaders.CORRELATION_ID)}
 */
public record RecipientResult(
        String decryptedPayload,
        Map<String, String> protocolHeaders,
        String correlationId) {

    public RecipientResult {
        Objects.requireNonNull(decryptedPayload, "decryptedPayload");
        Objects.requireNonNull(protocolHeaders, "protocolHeaders");
        Objects.requireNonNull(correlationId, "correlationId");
        protocolHeaders = Map.copyOf(protocolHeaders);
    }
}
