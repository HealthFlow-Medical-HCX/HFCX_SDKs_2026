package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#TRANSPORT} ({@code ERR-T-001}).
 *
 * <p>Transport-layer failure on an outbound request.
 */
public final class TransportException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#TRANSPORT}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.TRANSPORT.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public TransportException(String message) {
        super(ErrorCode.TRANSPORT, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public TransportException(String message, Throwable cause) {
        super(ErrorCode.TRANSPORT, message, cause);
    }
}
