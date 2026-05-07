package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#TIMESTAMP_OUT_OF_RANGE} ({@code ERR-P-006}).
 *
 * <p>x-hcx-timestamp is outside the configured tolerance window.
 */
public final class TimestampOutOfRangeException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.TIMESTAMP_OUT_OF_RANGE.code();

    public TimestampOutOfRangeException(String message) {
        super(ErrorCode.TIMESTAMP_OUT_OF_RANGE, message);
    }

    public TimestampOutOfRangeException(String message, Throwable cause) {
        super(ErrorCode.TIMESTAMP_OUT_OF_RANGE, message, cause);
    }
}
