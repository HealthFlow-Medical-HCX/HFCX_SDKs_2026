package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Typed exception for {@link ErrorCode#AUTHENTICATION} ({@code ERR-T-002}).
 *
 * <p>Raised when an upstream identity provider rejects the SDK's
 * credentials with HTTP 401, when the recipient pipeline rejects an
 * inbound bearer token, or when a configured {@code BearerTokenValidator}
 * indicates failure.
 *
 * <p>Subtype of {@link TechnicalException} — catching that picks up both
 * generic transport failures ({@code ERR-T-001}) and auth failures.
 */
public final class AuthenticationException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format error code for an authentication failure. */
    public static final String CODE = ErrorCode.AUTHENTICATION.code();

    /**
     * @param message a free-form description of the specific failure
     */
    public AuthenticationException(String message) {
        super(ErrorCode.AUTHENTICATION, message);
    }

    /**
     * @param message a free-form description of the specific failure
     * @param cause   the underlying cause (typically the IdP transport failure)
     */
    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCode.AUTHENTICATION, message, cause);
    }
}
