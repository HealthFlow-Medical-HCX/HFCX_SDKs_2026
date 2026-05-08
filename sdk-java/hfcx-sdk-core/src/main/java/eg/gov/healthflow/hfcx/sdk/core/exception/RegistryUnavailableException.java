package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#REGISTRY_UNAVAILABLE} ({@code ERR-T-003}).
 *
 * <p>Participant registry is unreachable.
 */
public final class RegistryUnavailableException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#REGISTRY_UNAVAILABLE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.REGISTRY_UNAVAILABLE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public RegistryUnavailableException(String message) {
        super(ErrorCode.REGISTRY_UNAVAILABLE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public RegistryUnavailableException(String message, Throwable cause) {
        super(ErrorCode.REGISTRY_UNAVAILABLE, message, cause);
    }
}
