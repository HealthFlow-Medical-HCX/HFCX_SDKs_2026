package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PARTICIPANT_NOT_FOUND} ({@code ERR-B-001}).
 *
 * <p>Participant code is not present in the platform registry.
 */
public final class ParticipantNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#PARTICIPANT_NOT_FOUND}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.PARTICIPANT_NOT_FOUND.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public ParticipantNotFoundException(String message) {
        super(ErrorCode.PARTICIPANT_NOT_FOUND, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public ParticipantNotFoundException(String message, Throwable cause) {
        super(ErrorCode.PARTICIPANT_NOT_FOUND, message, cause);
    }
}
