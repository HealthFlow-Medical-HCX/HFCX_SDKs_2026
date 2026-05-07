package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#RECIPIENT_CODE_MISMATCH} ({@code ERR-P-003}).
 *
 * <p>Inbound x-hcx-recipient_code does not match the local participant code.
 */
public final class RecipientCodeMismatchException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.RECIPIENT_CODE_MISMATCH.code();

    public RecipientCodeMismatchException(String message) {
        super(ErrorCode.RECIPIENT_CODE_MISMATCH, message);
    }

    public RecipientCodeMismatchException(String message, Throwable cause) {
        super(ErrorCode.RECIPIENT_CODE_MISMATCH, message, cause);
    }
}
