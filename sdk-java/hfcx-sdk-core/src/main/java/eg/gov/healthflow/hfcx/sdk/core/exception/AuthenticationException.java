package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Raised when an upstream identity provider (Keycloak today; potentially
 * other OIDC-compliant providers in future) rejects the SDK's credentials
 * with HTTP 401. Carries error code {@code ERR-T-002} on the wire.
 *
 * <p>Modelled as a subtype of {@link TechnicalException} because the
 * failure surfaces at the transport tier rather than from a protocol
 * header validation or business rule. Catching {@code TechnicalException}
 * therefore picks up both this and generic transport failures
 * ({@code ERR-T-001}).
 */
public class AuthenticationException extends TechnicalException {

    private static final long serialVersionUID = 1L;

    /** Wire-format error code for an authentication failure. */
    public static final String CODE = "ERR-T-002";

    public AuthenticationException(String message) {
        super(CODE, message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
