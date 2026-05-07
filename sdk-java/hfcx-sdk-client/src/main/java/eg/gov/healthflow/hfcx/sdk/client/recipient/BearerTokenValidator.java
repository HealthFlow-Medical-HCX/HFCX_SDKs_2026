package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;

/**
 * Verifies the {@code Authorization: Bearer …} token attached to an
 * inbound request. Pluggable so participants can use Keycloak's JWKS,
 * an mTLS-fronted proxy, or any other identity provider.
 *
 * <p>The SDK does NOT ship a default implementation that "trusts
 * everything" — that would be a security footgun. If
 * {@link Layer#BEARER} is enabled but no validator is configured,
 * {@code RecipientHandler} fails at construction time.
 */
@FunctionalInterface
public interface BearerTokenValidator {

    /**
     * @param authorizationHeader the full {@code Authorization} header
     *     as received (e.g. {@code "Bearer eyJ…"}); may be null.
     * @throws AuthenticationException if the token is missing, malformed,
     *     expired, or signed by an untrusted issuer.
     */
    void validate(String authorizationHeader);
}
