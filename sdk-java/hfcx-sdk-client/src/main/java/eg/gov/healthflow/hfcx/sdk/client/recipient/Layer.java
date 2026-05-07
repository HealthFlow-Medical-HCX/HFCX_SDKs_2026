package eg.gov.healthflow.hfcx.sdk.client.recipient;

/**
 * The four independently-toggleable validation layers that
 * {@link RecipientHandler} runs against an incoming HFCX request, in
 * order. The order is fixed (cheap rejections first) to mirror the
 * platform's {@code JwePayloadProcessor}.
 *
 * <ol>
 *   <li>{@link #BEARER} — JWT bearer-token signature + claims.</li>
 *   <li>{@link #HEADERS} — protocol headers (sender/recipient codes,
 *       correlation ID, timestamp freshness, api-call-id format).</li>
 *   <li>{@link #FHIR} — FHIR R4 schema and Egyptian IG profile validation.</li>
 *   <li>{@link #EGYPTIAN} — National-ID / phone / IBAN / governorate
 *       checks on the resources inside the payload.</li>
 * </ol>
 *
 * <p>Each layer can be enabled or disabled independently via
 * {@link RecipientHandler.Builder#enable(Layer, boolean)}. Default for
 * every layer is enabled.
 */
public enum Layer {
    BEARER,
    HEADERS,
    FHIR,
    EGYPTIAN
}
