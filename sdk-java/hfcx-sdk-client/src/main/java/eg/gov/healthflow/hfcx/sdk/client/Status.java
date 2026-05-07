package eg.gov.healthflow.hfcx.sdk.client;

/**
 * Outcome of an HFCX outbound request as observed at the SDK call site.
 *
 * <p>Mapping to platform behaviour:
 * <ul>
 *   <li>{@link #ACCEPTED} — gateway returned HTTP 202; the recipient will
 *       process asynchronously.</li>
 *   <li>{@link #REJECTED} — gateway or recipient returned a 4xx response
 *       attached to a typed exception; the response object is surfaced
 *       only when the caller chose to catch the exception.</li>
 *   <li>{@link #STUBBED} — used by Sprint J3 stubs that construct headers
 *       and propagate correlation IDs but do not yet POST to the gateway.
 *       Replaced by {@link #ACCEPTED} or {@link #REJECTED} when Sprint J4
 *       lands the outbound encryption + HTTP path.</li>
 * </ul>
 */
public enum Status {
    ACCEPTED,
    REJECTED,
    STUBBED
}
