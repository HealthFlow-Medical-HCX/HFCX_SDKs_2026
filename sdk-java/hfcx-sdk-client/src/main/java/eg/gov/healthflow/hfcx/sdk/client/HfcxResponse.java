package eg.gov.healthflow.hfcx.sdk.client;

import java.util.Objects;

/**
 * Common shape returned by every {@code HfcxClient} sender method.
 *
 * <p>The HFCX gateway returns HTTP 202 + a correlation ID for every
 * accepted request (eligibility, preauth, claim, communication, payment
 * notice). The recipient processes the request asynchronously and
 * delivers the response over a separate inbound channel. There is no
 * synchronous payload to surface, so a single response shape is
 * sufficient regardless of operation.
 *
 * <p>Tracking via the {@code correlationId} field is the cross-SDK
 * invariant — callers log it, persist it, and look up the eventual
 * inbound response by it.
 *
 * @param correlationId the correlation ID used for this transaction;
 *     either supplied by the caller on the request, or auto-generated
 *     UUID4 by the SDK if the caller passed {@code null}.
 * @param status        the gateway-observed status (see {@link Status}).
 */
public record HfcxResponse(String correlationId, Status status) {

    public HfcxResponse {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(status, "status");
    }
}
