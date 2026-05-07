package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#TRANSPORT} ({@code ERR-T-001}).
 *
 * <p>Transport-layer failure on an outbound request.
 */
public final class TransportException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.TRANSPORT.code();

    public TransportException(String message) {
        super(ErrorCode.TRANSPORT, message);
    }

    public TransportException(String message, Throwable cause) {
        super(ErrorCode.TRANSPORT, message, cause);
    }
}
