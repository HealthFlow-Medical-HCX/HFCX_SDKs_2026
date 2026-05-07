package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BUNDLE_MISSING_TYPE} ({@code ERR-B-003}).
 *
 * <p>Bundle.type field is required by the Egyptian IG.
 */
public final class BundleMissingTypeException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.BUNDLE_MISSING_TYPE.code();

    public BundleMissingTypeException(String message) {
        super(ErrorCode.BUNDLE_MISSING_TYPE, message);
    }

    public BundleMissingTypeException(String message, Throwable cause) {
        super(ErrorCode.BUNDLE_MISSING_TYPE, message, cause);
    }
}
