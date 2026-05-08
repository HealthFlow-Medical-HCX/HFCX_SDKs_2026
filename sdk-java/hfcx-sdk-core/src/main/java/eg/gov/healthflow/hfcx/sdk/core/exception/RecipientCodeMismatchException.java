package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#RECIPIENT_CODE_MISMATCH} ({@code ERR-P-003}).
 *
 * <p>Inbound x-hcx-recipient_code does not match the local participant code.
 */
public final class RecipientCodeMismatchException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#RECIPIENT_CODE_MISMATCH}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.RECIPIENT_CODE_MISMATCH.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public RecipientCodeMismatchException(String message) {
        super(ErrorCode.RECIPIENT_CODE_MISMATCH, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public RecipientCodeMismatchException(String message, Throwable cause) {
        super(ErrorCode.RECIPIENT_CODE_MISMATCH, message, cause);
    }
}
