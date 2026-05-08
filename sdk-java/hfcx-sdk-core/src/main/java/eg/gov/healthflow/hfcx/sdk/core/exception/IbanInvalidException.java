package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#IBAN_INVALID} ({@code ERR-B-008}).
 *
 * <p>Egyptian IBAN value fails the ISO 13616 mod-97 check.
 */
public final class IbanInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#IBAN_INVALID}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.IBAN_INVALID.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public IbanInvalidException(String message) {
        super(ErrorCode.IBAN_INVALID, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public IbanInvalidException(String message, Throwable cause) {
        super(ErrorCode.IBAN_INVALID, message, cause);
    }
}
