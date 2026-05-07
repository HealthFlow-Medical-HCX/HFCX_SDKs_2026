package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#SIGNATURE_VERIFICATION_FAILED} ({@code ERR-P-009}).
 *
 * <p>Detached signature verification failed on the inbound JWE.
 */
public final class SignatureVerificationFailedException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.SIGNATURE_VERIFICATION_FAILED.code();

    public SignatureVerificationFailedException(String message) {
        super(ErrorCode.SIGNATURE_VERIFICATION_FAILED, message);
    }

    public SignatureVerificationFailedException(String message, Throwable cause) {
        super(ErrorCode.SIGNATURE_VERIFICATION_FAILED, message, cause);
    }
}
