package eg.gov.healthflow.hfcx.sdk.examples.recipient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot example showing how a participant running their own
 * HCX-API instance plugs {@code RecipientHandler} into a controller
 * chain. Run via:
 *
 * <pre>{@code
 *   ./mvnw -B -ntp -pl hfcx-sdk-examples spring-boot:run \
 *       -Dspring-boot.run.main-class=eg.gov.healthflow.hfcx.sdk.examples.recipient.RecipientApplication
 * }</pre>
 *
 * <p>The application exposes the five HFCX endpoints (claim, preauth,
 * eligibility, communication, payment notice). All five route through
 * {@link RecipientController#handle(String, String, String, String, String, String, String)}.
 *
 * <p>This is an EXAMPLE, not a production-grade deployment. It uses an
 * in-memory test key for decryption and accepts any bearer token. Wire
 * a real {@link eg.gov.healthflow.hfcx.sdk.client.recipient.LocalKeyProvider}
 * (file or Vault) and a real
 * {@link eg.gov.healthflow.hfcx.sdk.client.recipient.BearerTokenValidator}
 * (Keycloak JWKS) before deploying.
 */
@SpringBootApplication
public class RecipientApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipientApplication.class, args);
    }
}
