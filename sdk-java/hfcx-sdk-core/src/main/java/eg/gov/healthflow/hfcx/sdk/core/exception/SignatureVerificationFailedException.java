package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#SIGNATURE_VERIFICATION_FAILED} ({@code ERR-P-009}).
 *
 * <p>Detached signature verification failed on the inbound JWE.
 */
public final class SignatureVerificationFailedException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#SIGNATURE_VERIFICATION_FAILED}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.SIGNATURE_VERIFICATION_FAILED.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public SignatureVerificationFailedException(String message) {
        super(ErrorCode.SIGNATURE_VERIFICATION_FAILED, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public SignatureVerificationFailedException(String message, Throwable cause) {
        super(ErrorCode.SIGNATURE_VERIFICATION_FAILED, message, cause);
    }
}
