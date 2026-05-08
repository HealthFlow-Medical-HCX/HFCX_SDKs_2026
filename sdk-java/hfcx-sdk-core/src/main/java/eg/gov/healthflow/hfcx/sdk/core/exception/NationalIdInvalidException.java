package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#NATIONAL_ID_INVALID} ({@code ERR-B-006}).
 *
 * <p>Egyptian National ID value fails structural validation.
 */
public final class NationalIdInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#NATIONAL_ID_INVALID}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.NATIONAL_ID_INVALID.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public NationalIdInvalidException(String message) {
        super(ErrorCode.NATIONAL_ID_INVALID, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public NationalIdInvalidException(String message, Throwable cause) {
        super(ErrorCode.NATIONAL_ID_INVALID, message, cause);
    }
}
