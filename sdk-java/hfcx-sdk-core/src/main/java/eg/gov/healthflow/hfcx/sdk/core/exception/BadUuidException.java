package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_UUID} ({@code ERR-P-004}).
 *
 * <p>A header value claiming to be a UUID is not parseable as one.
 */
public final class BadUuidException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#BAD_UUID}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.BAD_UUID.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public BadUuidException(String message) {
        super(ErrorCode.BAD_UUID, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public BadUuidException(String message, Throwable cause) {
        super(ErrorCode.BAD_UUID, message, cause);
    }
}
