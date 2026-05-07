package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#IBAN_INVALID} ({@code ERR-B-008}).
 *
 * <p>Egyptian IBAN value fails the ISO 13616 mod-97 check.
 */
public final class IbanInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.IBAN_INVALID.code();

    public IbanInvalidException(String message) {
        super(ErrorCode.IBAN_INVALID, message);
    }

    public IbanInvalidException(String message, Throwable cause) {
        super(ErrorCode.IBAN_INVALID, message, cause);
    }
}
