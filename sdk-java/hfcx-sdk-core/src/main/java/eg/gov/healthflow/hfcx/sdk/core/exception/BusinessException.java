package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Raised for business-rule violations (FHIR IG conformance, Egyptian field
 * validation, invariant failures). Codes are of the form {@code ERR-B-xxx}.
 */
public class BusinessException extends HfcxException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String code, String message) {
        super(code, message);
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BusinessException(ErrorCode code, String message) {
        super(code, message);
    }

    public BusinessException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
