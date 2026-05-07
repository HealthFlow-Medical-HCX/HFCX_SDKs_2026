package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#JWE_ALGORITHM_REJECTED} ({@code ERR-P-002}).
 *
 * <p>JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM.
 */
public final class JweAlgorithmRejectedException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.JWE_ALGORITHM_REJECTED.code();

    public JweAlgorithmRejectedException(String message) {
        super(ErrorCode.JWE_ALGORITHM_REJECTED, message);
    }

    public JweAlgorithmRejectedException(String message, Throwable cause) {
        super(ErrorCode.JWE_ALGORITHM_REJECTED, message, cause);
    }
}
