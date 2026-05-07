package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PARTICIPANT_NOT_FOUND} ({@code ERR-B-001}).
 *
 * <p>Participant code is not present in the platform registry.
 */
public final class ParticipantNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.PARTICIPANT_NOT_FOUND.code();

    public ParticipantNotFoundException(String message) {
        super(ErrorCode.PARTICIPANT_NOT_FOUND, message);
    }

    public ParticipantNotFoundException(String message, Throwable cause) {
        super(ErrorCode.PARTICIPANT_NOT_FOUND, message, cause);
    }
}
