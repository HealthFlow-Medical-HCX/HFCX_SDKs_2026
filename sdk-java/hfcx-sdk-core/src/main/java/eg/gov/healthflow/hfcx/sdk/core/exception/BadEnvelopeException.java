package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_ENVELOPE} ({@code ERR-P-008}).
 *
 * <p>Request body envelope is malformed at the transport tier.
 */
public final class BadEnvelopeException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#BAD_ENVELOPE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.BAD_ENVELOPE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public BadEnvelopeException(String message) {
        super(ErrorCode.BAD_ENVELOPE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public BadEnvelopeException(String message, Throwable cause) {
        super(ErrorCode.BAD_ENVELOPE, message, cause);
    }
}
