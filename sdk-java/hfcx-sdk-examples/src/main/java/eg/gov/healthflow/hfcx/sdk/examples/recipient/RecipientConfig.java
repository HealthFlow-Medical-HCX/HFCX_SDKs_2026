package eg.gov.healthflow.hfcx.sdk.examples.recipient;

import eg.gov.healthflow.hfcx.sdk.client.recipient.BearerTokenValidator;
import eg.gov.healthflow.hfcx.sdk.client.recipient.FileLocalKeyProvider;
import eg.gov.healthflow.hfcx.sdk.client.recipient.LocalKeyProvider;
import eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientHandler;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires {@link RecipientHandler} into the Spring application context.
 * Profiles control which key provider and bearer validator are bound:
 *
 * <ul>
 *   <li>Default — file-based key provider, real Keycloak bearer
 *       validator (a stub here; production deployments plug in their
 *       own JWKS-backed implementation).</li>
 *   <li>{@code test} — overridden by the test class to inject in-memory
 *       fakes.</li>
 * </ul>
 */
@Configuration
@Profile("!test")
public class RecipientConfig {

    @Bean
    public LocalKeyProvider localKeyProvider(
            @Value("${hfcx.recipient.private-key-path}") String keyPath) {
        return new FileLocalKeyProvider(keyPath);
    }

    /**
     * Production deployments swap this for a JWKS-backed validator
     * pointed at the participant's own Keycloak realm. Sprint J5 ships
     * the SDK without a default trust-everything implementation by
     * design — this stub fails fast unless the bearer header looks
     * structurally valid.
     */
    @Bean
    public BearerTokenValidator bearerTokenValidator() {
        return authHeader -> {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new AuthenticationException("Missing or malformed Authorization header");
            }
        };
    }

    @Bean
    public RecipientHandler recipientHandler(
            LocalKeyProvider keyProvider,
            BearerTokenValidator bearerValidator,
            @Value("${hfcx.recipient.participant-code}") String participantCode) {
        return RecipientHandler.builder()
                .keyProvider(keyProvider)
                .bearerTokenValidator(bearerValidator)
                .localParticipantCode(participantCode)
                .build();
    }
}
