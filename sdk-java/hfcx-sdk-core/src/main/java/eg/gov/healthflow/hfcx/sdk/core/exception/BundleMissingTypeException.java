package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#BUNDLE_MISSING_TYPE} ({@code ERR-B-003}).
 *
 * <p>Bundle.type field is required by the Egyptian IG.
 */
public final class BundleMissingTypeException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#BUNDLE_MISSING_TYPE}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.BUNDLE_MISSING_TYPE.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public BundleMissingTypeException(String message) {
        super(ErrorCode.BUNDLE_MISSING_TYPE, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public BundleMissingTypeException(String message, Throwable cause) {
        super(ErrorCode.BUNDLE_MISSING_TYPE, message, cause);
    }
}
