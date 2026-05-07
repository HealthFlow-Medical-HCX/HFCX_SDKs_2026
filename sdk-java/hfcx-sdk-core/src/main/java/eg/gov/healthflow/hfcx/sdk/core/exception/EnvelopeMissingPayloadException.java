package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#ENVELOPE_MISSING_PAYLOAD} ({@code ERR-B-010}).
 *
 * <p>Request body envelope is missing the required 'payload' field.
 */
public final class EnvelopeMissingPayloadException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.ENVELOPE_MISSING_PAYLOAD.code();

    public EnvelopeMissingPayloadException(String message) {
        super(ErrorCode.ENVELOPE_MISSING_PAYLOAD, message);
    }

    public EnvelopeMissingPayloadException(String message, Throwable cause) {
        super(ErrorCode.ENVELOPE_MISSING_PAYLOAD, message, cause);
    }
}
