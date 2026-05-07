package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BAD_FHIR_JSON} ({@code ERR-B-009}).
 *
 * <p>FHIR payload is not valid JSON.
 */
public final class BadFhirJsonException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.BAD_FHIR_JSON.code();

    public BadFhirJsonException(String message) {
        super(ErrorCode.BAD_FHIR_JSON, message);
    }

    public BadFhirJsonException(String message, Throwable cause) {
        super(ErrorCode.BAD_FHIR_JSON, message, cause);
    }
}
