package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#CRYPTOGRAPHIC_FAILURE} ({@code ERR-T-005}).
 *
 * <p>JOSE library reported a cryptographic failure.
 */
public final class CryptographicFailureException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#CRYPTOGRAPHIC_FAILURE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.CRYPTOGRAPHIC_FAILURE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public CryptographicFailureException(String message) {
        super(ErrorCode.CRYPTOGRAPHIC_FAILURE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public CryptographicFailureException(String message, Throwable cause) {
        super(ErrorCode.CRYPTOGRAPHIC_FAILURE, message, cause);
    }
}
