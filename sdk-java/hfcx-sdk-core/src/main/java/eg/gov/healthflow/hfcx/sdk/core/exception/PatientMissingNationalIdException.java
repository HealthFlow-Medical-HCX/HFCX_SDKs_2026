package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PATIENT_MISSING_NATIONAL_ID} ({@code ERR-B-004}).
 *
 * <p>Patient resource has no identifier with the National-ID system URI.
 */
public final class PatientMissingNationalIdException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.PATIENT_MISSING_NATIONAL_ID.code();

    public PatientMissingNationalIdException(String message) {
        super(ErrorCode.PATIENT_MISSING_NATIONAL_ID, message);
    }

    public PatientMissingNationalIdException(String message, Throwable cause) {
        super(ErrorCode.PATIENT_MISSING_NATIONAL_ID, message, cause);
    }
}
