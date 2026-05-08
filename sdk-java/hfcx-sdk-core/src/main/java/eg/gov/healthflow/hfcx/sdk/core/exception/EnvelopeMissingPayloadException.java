package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#ENVELOPE_MISSING_PAYLOAD} ({@code ERR-B-010}).
 *
 * <p>Request body envelope is missing the required 'payload' field.
 */
public final class EnvelopeMissingPayloadException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#ENVELOPE_MISSING_PAYLOAD}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.ENVELOPE_MISSING_PAYLOAD.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public EnvelopeMissingPayloadException(String message) {
        super(ErrorCode.ENVELOPE_MISSING_PAYLOAD, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public EnvelopeMissingPayloadException(String message, Throwable cause) {
        super(ErrorCode.ENVELOPE_MISSING_PAYLOAD, message, cause);
    }
}
