package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#TIMESTAMP_OUT_OF_RANGE} ({@code ERR-P-006}).
 *
 * <p>x-hcx-timestamp is outside the configured tolerance window.
 */
public final class TimestampOutOfRangeException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#TIMESTAMP_OUT_OF_RANGE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.TIMESTAMP_OUT_OF_RANGE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public TimestampOutOfRangeException(String message) {
        super(ErrorCode.TIMESTAMP_OUT_OF_RANGE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public TimestampOutOfRangeException(String message, Throwable cause) {
        super(ErrorCode.TIMESTAMP_OUT_OF_RANGE, message, cause);
    }
}
