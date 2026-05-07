package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#SENDER_UNKNOWN} ({@code ERR-P-007}).
 *
 * <p>Sender participant code is not registered.
 */
public final class SenderUnknownException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.SENDER_UNKNOWN.code();

    public SenderUnknownException(String message) {
        super(ErrorCode.SENDER_UNKNOWN, message);
    }

    public SenderUnknownException(String message, Throwable cause) {
        super(ErrorCode.SENDER_UNKNOWN, message, cause);
    }
}
