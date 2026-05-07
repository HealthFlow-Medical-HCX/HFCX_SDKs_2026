package eg.gov.healthflow.hfcx.sdk.client.protocol;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Constructs the protocol header set the HFCX gateway expects on every
 * outbound request, per Integration Guide §24.5.
 *
 * <h2>Header naming — Gap 7 backward compatibility</h2>
 *
 * The platform currently accepts the mixed hyphen+underscore form for
 * header names that combine multiple words. SDK release 1.x ships
 * exactly the names the platform accepts today; if §24.5 ever
 * normalises to all-hyphen, that's a coordinated cross-SDK change
 * (bumps the major version of every SDK in lockstep).
 *
 * <table>
 *     <caption>Wire-format header names</caption>
 *     <tr><th>Constant</th><th>Wire name</th></tr>
 *     <tr><td>{@link #SENDER_CODE}</td><td>{@code x-hcx-sender_code}</td></tr>
 *     <tr><td>{@link #RECIPIENT_CODE}</td><td>{@code x-hcx-recipient_code}</td></tr>
 *     <tr><td>{@link #CORRELATION_ID}</td><td>{@code x-hcx-correlation_id}</td></tr>
 *     <tr><td>{@link #TIMESTAMP}</td><td>{@code x-hcx-timestamp}</td></tr>
 *     <tr><td>{@link #API_CALL_ID}</td><td>{@code x-hcx-api-call-id}</td></tr>
 * </table>
 */
public final class ProtocolHeaders {

    public static final String SENDER_CODE = "x-hcx-sender_code";
    public static final String RECIPIENT_CODE = "x-hcx-recipient_code";
    public static final String CORRELATION_ID = "x-hcx-correlation_id";
    public static final String TIMESTAMP = "x-hcx-timestamp";
    public static final String API_CALL_ID = "x-hcx-api-call-id";

    private ProtocolHeaders() {
        // Static helper.
    }

    /**
     * Build the protocol header map for an outbound request. Keys are returned
     * in deterministic order ({@code sender_code}, {@code recipient_code},
     * {@code correlation_id}, {@code timestamp}, {@code api-call-id}) so
     * tests can assert byte-identical wire content.
     *
     * @param senderCode    HFCX participant code of the sender
     * @param recipientCode HFCX participant code of the recipient
     * @param correlationId logical correlation ID for the transaction
     * @param timestamp     instant the request was constructed; serialised as ISO-8601
     * @param apiCallId     per-call unique ID; survives only this single HTTP call
     */
    public static Map<String, String> build(
            String senderCode,
            String recipientCode,
            String correlationId,
            Instant timestamp,
            String apiCallId) {
        Objects.requireNonNull(senderCode, "senderCode");
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(apiCallId, "apiCallId");

        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put(SENDER_CODE, senderCode);
        headers.put(RECIPIENT_CODE, recipientCode);
        headers.put(CORRELATION_ID, correlationId);
        headers.put(TIMESTAMP, DateTimeFormatter.ISO_INSTANT.format(timestamp));
        headers.put(API_CALL_ID, apiCallId);
        return Collections.unmodifiableMap(headers);
    }
}
