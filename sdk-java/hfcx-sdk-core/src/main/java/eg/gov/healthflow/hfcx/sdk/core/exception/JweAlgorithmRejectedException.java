package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#JWE_ALGORITHM_REJECTED} ({@code ERR-P-002}).
 *
 * <p>JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM.
 */
public final class JweAlgorithmRejectedException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#JWE_ALGORITHM_REJECTED}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.JWE_ALGORITHM_REJECTED.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public JweAlgorithmRejectedException(String message) {
        super(ErrorCode.JWE_ALGORITHM_REJECTED, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public JweAlgorithmRejectedException(String message, Throwable cause) {
        super(ErrorCode.JWE_ALGORITHM_REJECTED, message, cause);
    }
}
