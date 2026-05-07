package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#GATEWAY_5XX} ({@code ERR-T-006}).
 *
 * <p>HFCX gateway returned a 5xx response after retry exhaustion.
 */
public final class Gateway5xxException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.GATEWAY_5XX.code();

    public Gateway5xxException(String message) {
        super(ErrorCode.GATEWAY_5XX, message);
    }

    public Gateway5xxException(String message, Throwable cause) {
        super(ErrorCode.GATEWAY_5XX, message, cause);
    }
}
