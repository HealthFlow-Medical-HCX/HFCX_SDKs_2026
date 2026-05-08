package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#KEY_UNAVAILABLE} ({@code ERR-T-004}).
 *
 * <p>Recipient private key cannot be loaded from its configured source.
 */
public final class KeyUnavailableException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#KEY_UNAVAILABLE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.KEY_UNAVAILABLE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public KeyUnavailableException(String message) {
        super(ErrorCode.KEY_UNAVAILABLE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public KeyUnavailableException(String message, Throwable cause) {
        super(ErrorCode.KEY_UNAVAILABLE, message, cause);
    }
}
