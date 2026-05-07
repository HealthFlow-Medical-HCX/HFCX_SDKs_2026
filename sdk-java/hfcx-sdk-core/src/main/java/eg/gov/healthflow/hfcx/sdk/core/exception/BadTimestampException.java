package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_TIMESTAMP} ({@code ERR-P-005}).
 *
 * <p>x-hcx-timestamp could not be parsed as ISO-8601.
 */
public final class BadTimestampException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.BAD_TIMESTAMP.code();

    public BadTimestampException(String message) {
        super(ErrorCode.BAD_TIMESTAMP, message);
    }

    public BadTimestampException(String message, Throwable cause) {
        super(ErrorCode.BAD_TIMESTAMP, message, cause);
    }
}
