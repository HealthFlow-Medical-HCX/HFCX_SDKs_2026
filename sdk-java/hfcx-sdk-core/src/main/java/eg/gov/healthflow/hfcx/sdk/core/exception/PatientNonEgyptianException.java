package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#PATIENT_NON_EGYPTIAN} ({@code ERR-B-005}).
 *
 * <p>Patient.address[0].country is not 'EG'.
 */
public final class PatientNonEgyptianException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#PATIENT_NON_EGYPTIAN}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.PATIENT_NON_EGYPTIAN.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public PatientNonEgyptianException(String message) {
        super(ErrorCode.PATIENT_NON_EGYPTIAN, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public PatientNonEgyptianException(String message, Throwable cause) {
        super(ErrorCode.PATIENT_NON_EGYPTIAN, message, cause);
    }
}
