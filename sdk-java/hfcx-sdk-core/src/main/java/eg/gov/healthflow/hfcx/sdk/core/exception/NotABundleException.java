package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#NOT_A_BUNDLE} ({@code ERR-B-002}).
 *
 * <p>Top-level FHIR resource is not a Bundle.
 */
public final class NotABundleException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#NOT_A_BUNDLE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.NOT_A_BUNDLE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public NotABundleException(String message) {
        super(ErrorCode.NOT_A_BUNDLE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public NotABundleException(String message, Throwable cause) {
        super(ErrorCode.NOT_A_BUNDLE, message, cause);
    }
}
