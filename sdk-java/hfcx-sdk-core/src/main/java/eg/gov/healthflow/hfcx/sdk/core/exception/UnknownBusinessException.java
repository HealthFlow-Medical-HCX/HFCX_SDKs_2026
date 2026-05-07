package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#UNKNOWN_BUSINESS} ({@code ERR-B-012}).
 *
 * <p>Unspecified business-rule failure (gateway error code missing or unparseable).
 */
public final class UnknownBusinessException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = ErrorCode.UNKNOWN_BUSINESS.code();

    public UnknownBusinessException(String message) {
        super(ErrorCode.UNKNOWN_BUSINESS, message);
    }

    public UnknownBusinessException(String message, Throwable cause) {
        super(ErrorCode.UNKNOWN_BUSINESS, message, cause);
    }
}
