package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Raised for technical/transport failures (network timeout, registry
 * unavailable, Keycloak 5xx, key-store IO). Codes are of the form
 * {@code ERR-T-xxx}.
 */
public class TechnicalException extends HfcxException {

    private static final long serialVersionUID = 1L;

    public TechnicalException(String code, String message) {
        super(code, message);
    }

    public TechnicalException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
