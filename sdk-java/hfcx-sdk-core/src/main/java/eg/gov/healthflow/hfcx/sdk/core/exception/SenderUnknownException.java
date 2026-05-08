package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#SENDER_UNKNOWN} ({@code ERR-P-007}).
 *
 * <p>Sender participant code is not registered.
 */
public final class SenderUnknownException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#SENDER_UNKNOWN}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.SENDER_UNKNOWN.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public SenderUnknownException(String message) {
        super(ErrorCode.SENDER_UNKNOWN, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public SenderUnknownException(String message, Throwable cause) {
        super(ErrorCode.SENDER_UNKNOWN, message, cause);
    }
}
