package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#UNKNOWN_BUSINESS} ({@code ERR-B-012}).
 *
 * <p>Unspecified business-rule failure (gateway error code missing or unparseable).
 */
public final class UnknownBusinessException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** Wire-format code identical to {@link ErrorCode#UNKNOWN_BUSINESS}.{@link ErrorCode#code() code()}. */
    public static final String CODE = ErrorCode.UNKNOWN_BUSINESS.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public UnknownBusinessException(String message) {
        super(ErrorCode.UNKNOWN_BUSINESS, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically a JOSE / IO / parser exception)
     */
    public UnknownBusinessException(String message, Throwable cause) {
        super(ErrorCode.UNKNOWN_BUSINESS, message, cause);
    }
}
