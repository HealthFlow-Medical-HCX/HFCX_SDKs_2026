package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_ENVELOPE} ({@code ERR-P-008}).
 *
 * <p>Request body envelope is malformed at the transport tier.
 */
public final class BadEnvelopeException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.BAD_ENVELOPE.code();

    public BadEnvelopeException(String message) {
        super(ErrorCode.BAD_ENVELOPE, message);
    }

    public BadEnvelopeException(String message, Throwable cause) {
        super(ErrorCode.BAD_ENVELOPE, message, cause);
    }
}
