package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Raised for protocol-level failures (header validation, JWE structural
 * issues, schema violations). Codes are of the form {@code ERR-P-xxx}.
 */
public class ProtocolException extends HfcxException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String code, String message) {
        super(code, message);
    }

    public ProtocolException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public ProtocolException(ErrorCode code, String message) {
        super(code, message);
    }

    public ProtocolException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
