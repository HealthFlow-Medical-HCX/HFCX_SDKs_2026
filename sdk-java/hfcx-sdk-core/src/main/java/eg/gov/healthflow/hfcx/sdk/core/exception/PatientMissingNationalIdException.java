package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PATIENT_MISSING_NATIONAL_ID} ({@code ERR-B-004}).
 *
 * <p>Patient resource has no identifier with the National-ID system URI.
 */
public final class PatientMissingNationalIdException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#PATIENT_MISSING_NATIONAL_ID}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.PATIENT_MISSING_NATIONAL_ID.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public PatientMissingNationalIdException(String message) {
        super(ErrorCode.PATIENT_MISSING_NATIONAL_ID, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public PatientMissingNationalIdException(String message, Throwable cause) {
        super(ErrorCode.PATIENT_MISSING_NATIONAL_ID, message, cause);
    }
}
