package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#NOT_A_BUNDLE} ({@code ERR-B-002}).
 *
 * <p>Top-level FHIR resource is not a Bundle.
 */
public final class NotABundleException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.NOT_A_BUNDLE.code();

    public NotABundleException(String message) {
        super(ErrorCode.NOT_A_BUNDLE, message);
    }

    public NotABundleException(String message, Throwable cause) {
        super(ErrorCode.NOT_A_BUNDLE, message, cause);
    }
}
