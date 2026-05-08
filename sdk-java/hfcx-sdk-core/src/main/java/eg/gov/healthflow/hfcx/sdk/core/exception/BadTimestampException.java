package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_TIMESTAMP} ({@code ERR-P-005}).
 *
 * <p>x-hcx-timestamp could not be parsed as ISO-8601.
 */
public final class BadTimestampException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#BAD_TIMESTAMP}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.BAD_TIMESTAMP.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public BadTimestampException(String message) {
        super(ErrorCode.BAD_TIMESTAMP, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public BadTimestampException(String message, Throwable cause) {
        super(ErrorCode.BAD_TIMESTAMP, message, cause);
    }
}
