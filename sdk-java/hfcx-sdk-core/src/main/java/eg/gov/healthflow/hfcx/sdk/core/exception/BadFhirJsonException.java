package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_FHIR_JSON} ({@code ERR-B-009}).
 *
 * <p>FHIR payload is not valid JSON.
 */
public final class BadFhirJsonException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#BAD_FHIR_JSON}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.BAD_FHIR_JSON.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public BadFhirJsonException(String message) {
        super(ErrorCode.BAD_FHIR_JSON, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public BadFhirJsonException(String message, Throwable cause) {
        super(ErrorCode.BAD_FHIR_JSON, message, cause);
    }
}
