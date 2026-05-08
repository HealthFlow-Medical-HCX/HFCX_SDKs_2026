package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#GATEWAY_5XX} ({@code ERR-T-006}).
 *
 * <p>HFCX gateway returned a 5xx response after retry exhaustion.
 */
public final class Gateway5xxException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#GATEWAY_5XX}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.GATEWAY_5XX.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public Gateway5xxException(String message) {
        super(ErrorCode.GATEWAY_5XX, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public Gateway5xxException(String message, Throwable cause) {
        super(ErrorCode.GATEWAY_5XX, message, cause);
    }
}
