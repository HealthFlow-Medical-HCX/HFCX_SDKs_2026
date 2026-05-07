package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#MISSING_HEADER} ({@code ERR-P-001}).
 *
 * <p>Required protocol header is missing or empty.
 */
public final class MissingHeaderException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.MISSING_HEADER.code();

    public MissingHeaderException(String message) {
        super(ErrorCode.MISSING_HEADER, message);
    }

    public MissingHeaderException(String message, Throwable cause) {
        super(ErrorCode.MISSING_HEADER, message, cause);
    }
}
