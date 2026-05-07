package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#CRYPTOGRAPHIC_FAILURE} ({@code ERR-T-005}).
 *
 * <p>JOSE library reported a cryptographic failure.
 */
public final class CryptographicFailureException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.CRYPTOGRAPHIC_FAILURE.code();

    public CryptographicFailureException(String message) {
        super(ErrorCode.CRYPTOGRAPHIC_FAILURE, message);
    }

    public CryptographicFailureException(String message, Throwable cause) {
        super(ErrorCode.CRYPTOGRAPHIC_FAILURE, message, cause);
    }
}
