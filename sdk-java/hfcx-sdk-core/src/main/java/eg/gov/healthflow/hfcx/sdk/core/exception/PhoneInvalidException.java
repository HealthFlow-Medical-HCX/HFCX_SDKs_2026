package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PHONE_INVALID} ({@code ERR-B-007}).
 *
 * <p>Egyptian mobile phone value is not in a recognised format.
 */
public final class PhoneInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#PHONE_INVALID}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.PHONE_INVALID.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public PhoneInvalidException(String message) {
        super(ErrorCode.PHONE_INVALID, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public PhoneInvalidException(String message, Throwable cause) {
        super(ErrorCode.PHONE_INVALID, message, cause);
    }
}
