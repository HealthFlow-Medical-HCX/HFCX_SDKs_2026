package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#MISSING_HEADER} ({@code ERR-P-001}).
 *
 * <p>Required protocol header is missing or empty.
 */
public final class MissingHeaderException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#MISSING_HEADER}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.MISSING_HEADER.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public MissingHeaderException(String message) {
        super(ErrorCode.MISSING_HEADER, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public MissingHeaderException(String message, Throwable cause) {
        super(ErrorCode.MISSING_HEADER, message, cause);
    }
}
