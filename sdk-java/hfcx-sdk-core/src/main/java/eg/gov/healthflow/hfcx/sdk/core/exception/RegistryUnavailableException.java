package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#REGISTRY_UNAVAILABLE} ({@code ERR-T-003}).
 *
 * <p>Participant registry is unreachable.
 */
public final class RegistryUnavailableException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.REGISTRY_UNAVAILABLE.code();

    public RegistryUnavailableException(String message) {
        super(ErrorCode.REGISTRY_UNAVAILABLE, message);
    }

    public RegistryUnavailableException(String message, Throwable cause) {
        super(ErrorCode.REGISTRY_UNAVAILABLE, message, cause);
    }
}
