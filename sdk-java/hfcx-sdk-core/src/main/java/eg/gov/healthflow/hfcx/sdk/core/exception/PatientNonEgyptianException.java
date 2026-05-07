package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PATIENT_NON_EGYPTIAN} ({@code ERR-B-005}).
 *
 * <p>Patient.address[0].country is not 'EG'.
 */
public final class PatientNonEgyptianException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.PATIENT_NON_EGYPTIAN.code();

    public PatientNonEgyptianException(String message) {
        super(ErrorCode.PATIENT_NON_EGYPTIAN, message);
    }

    public PatientNonEgyptianException(String message, Throwable cause) {
        super(ErrorCode.PATIENT_NON_EGYPTIAN, message, cause);
    }
}
