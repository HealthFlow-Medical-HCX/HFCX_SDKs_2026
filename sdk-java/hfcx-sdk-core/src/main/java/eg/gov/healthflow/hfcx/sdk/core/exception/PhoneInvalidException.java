package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PHONE_INVALID} ({@code ERR-B-007}).
 *
 * <p>Egyptian mobile phone value is not in a recognised format.
 */
public final class PhoneInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.PHONE_INVALID.code();

    public PhoneInvalidException(String message) {
        super(ErrorCode.PHONE_INVALID, message);
    }

    public PhoneInvalidException(String message, Throwable cause) {
        super(ErrorCode.PHONE_INVALID, message, cause);
    }
}
