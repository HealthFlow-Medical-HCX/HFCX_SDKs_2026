package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#NATIONAL_ID_INVALID} ({@code ERR-B-006}).
 *
 * <p>Egyptian National ID value fails structural validation.
 */
public final class NationalIdInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.NATIONAL_ID_INVALID.code();

    public NationalIdInvalidException(String message) {
        super(ErrorCode.NATIONAL_ID_INVALID, message);
    }

    public NationalIdInvalidException(String message, Throwable cause) {
        super(ErrorCode.NATIONAL_ID_INVALID, message, cause);
    }
}
