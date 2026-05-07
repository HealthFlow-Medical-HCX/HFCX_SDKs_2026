package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_UUID} ({@code ERR-P-004}).
 *
 * <p>A header value claiming to be a UUID is not parseable as one.
 */
public final class BadUuidException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.BAD_UUID.code();

    public BadUuidException(String message) {
        super(ErrorCode.BAD_UUID, message);
    }

    public BadUuidException(String message, Throwable cause) {
        super(ErrorCode.BAD_UUID, message, cause);
    }
}
