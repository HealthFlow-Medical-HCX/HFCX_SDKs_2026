package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#ENVELOPE_MALFORMED_JSON} ({@code ERR-B-011}).
 *
 * <p>Request body is not valid JSON.
 */
public final class EnvelopeMalformedJsonException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.ENVELOPE_MALFORMED_JSON.code();

    public EnvelopeMalformedJsonException(String message) {
        super(ErrorCode.ENVELOPE_MALFORMED_JSON, message);
    }

    public EnvelopeMalformedJsonException(String message, Throwable cause) {
        super(ErrorCode.ENVELOPE_MALFORMED_JSON, message, cause);
    }
}
