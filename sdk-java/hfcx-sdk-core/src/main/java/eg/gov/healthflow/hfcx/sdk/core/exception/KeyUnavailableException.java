package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#KEY_UNAVAILABLE} ({@code ERR-T-004}).
 *
 * <p>Recipient private key cannot be loaded from its configured source.
 */
public final class KeyUnavailableException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.KEY_UNAVAILABLE.code();

    public KeyUnavailableException(String message) {
        super(ErrorCode.KEY_UNAVAILABLE, message);
    }

    public KeyUnavailableException(String message, Throwable cause) {
        super(ErrorCode.KEY_UNAVAILABLE, message, cause);
    }
}
