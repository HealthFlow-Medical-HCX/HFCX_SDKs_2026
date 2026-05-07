package eg.gov.healthflow.hfcx.sdk.client.request;

/**
 * Common shape of every outbound HFCX request the SDK can emit. Sealed so
 * the SDK can dispatch via an exhaustive switch and downstream tooling
 * (validators, structured logging) sees the closed set.
 *
 * <p>{@code correlationId} is optional on the wire — pass {@code null}
 * to let the SDK auto-generate a UUID4 at call time. {@code recipientCode}
 * and {@code payload} are required; the records' canonical constructors
 * fail-fast on null.
 */
public sealed interface HfcxRequest
        permits CheckEligibilityRequest,
                SubmitPreauthRequest,
                SubmitClaimRequest,
                SendCommunicationRequest,
                NotifyPaymentRequest {

    /** HFCX participant code of the intended recipient (e.g. {@code "payerco@hcx-egypt"}). */
    String recipientCode();

    /**
     * Caller-supplied correlation ID, or {@code null} to let the SDK
     * generate one. Returned to the caller on the response.
     */
    String correlationId();

    /**
     * The FHIR R4 payload as a JSON string. In Sprint J3 this is opaque
     * to the SDK; Sprint J5 introduces FHIR validation against the
     * Egyptian IG before encryption.
     */
    String payload();
}
