package eg.gov.healthflow.hfcx.sdk.client;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live integration test against the {@code tests/integration/mock-payer}
 * container from the {@code HealthFlow-Medical-HCX/hfcx-platform} repo.
 * Required by Sprint J4 acceptance criterion 2.
 *
 * <h2>Why this is {@code @Disabled} by default</h2>
 *
 * The mock-payer container lives in the platform repo, which is outside
 * this SDK monorepo. Running this test requires:
 *
 * <ol>
 *   <li>A checkout of {@code HealthFlow-Medical-HCX/hfcx-platform} alongside
 *       this repo at a tag compatible with the SDK's bundled IG version
 *       (see {@code sdk-java/fhir-ig/PLATFORM_VERSION}).</li>
 *   <li>{@code docker compose up} from the platform repo's
 *       {@code tests/integration/} directory, which boots Keycloak, the
 *       Sunbird-RC participant registry, the gateway, and the mock-payer
 *       on known ports.</li>
 *   <li>The recipient cert PEM and private key issued to the mock-payer
 *       made available to the SDK test (typically at
 *       {@code tests/integration/fixtures/mock-payer-cert.pem}).</li>
 *   <li>Environment variables {@code HFCX_GATEWAY_URL},
 *       {@code HFCX_KEYCLOAK_TOKEN_URL},
 *       {@code HFCX_KEYCLOAK_CLIENT_ID},
 *       {@code HFCX_KEYCLOAK_CLIENT_SECRET},
 *       {@code HFCX_REGISTRY_BASE_URL}, and
 *       {@code HFCX_SENDER_PARTICIPANT_CODE} pointing at the running stack.</li>
 * </ol>
 *
 * <h2>Running</h2>
 *
 * Once the stack is up, enable this class with:
 *
 * <pre>{@code
 * ./mvnw -B -ntp test \
 *     -Dtest=PlatformMockPayerIntegrationTest \
 *     -Djunit.jupiter.tags=platform-integration \
 *     -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition
 * }</pre>
 *
 * The CI workflow will gain a separate job that performs steps 1–3 and
 * runs this class with the deactivation flag — that's tracked in the
 * platform repo's {@code docs/strategy/sdk-delivery-plan.md}.
 *
 * <h2>What this test asserts (when enabled)</h2>
 *
 * For each of the five sender methods (eligibility, preauth, claim,
 * communication, payment notice):
 *
 * <ul>
 *   <li>The SDK successfully encrypts a fixture FHIR Bundle with the
 *       mock-payer's published encryption key.</li>
 *   <li>The platform gateway accepts the envelope and returns HTTP 202.</li>
 *   <li>The {@link HfcxResponse#status()} is {@link Status#ACCEPTED}.</li>
 *   <li>The mock-payer's recorded inbox contains exactly one decrypted
 *       Bundle whose contents match the fixture (verified via the
 *       mock-payer's {@code /admin/inbox} debug endpoint).</li>
 * </ul>
 */
@Tag("platform-integration")
@Disabled("requires hfcx-platform tests/integration stack; see class Javadoc")
class PlatformMockPayerIntegrationTest {

    @Test
    void allFiveCyclesAcceptedByMockPayer() {
        throw new UnsupportedOperationException(
                "Implement when the platform-integration CI job lands. See class Javadoc.");
    }
}
