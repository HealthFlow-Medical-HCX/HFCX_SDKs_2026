package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#ENVELOPE_MALFORMED_JSON} ({@code ERR-B-011}).
 *
 * <p>Request body is not valid JSON.
 */
public final class EnvelopeMalformedJsonException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#ENVELOPE_MALFORMED_JSON}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.ENVELOPE_MALFORMED_JSON.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public EnvelopeMalformedJsonException(String message) {
        super(ErrorCode.ENVELOPE_MALFORMED_JSON, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public EnvelopeMalformedJsonException(String message, Throwable cause) {
        super(ErrorCode.ENVELOPE_MALFORMED_JSON, message, cause);
    }
}
