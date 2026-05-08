package eg.gov.healthflow.examples.submitclaim;

import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;
import eg.gov.healthflow.hfcx.sdk.client.HfcxResponse;
import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.registry.RegistryClient;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.HfcxException;

/**
 * Console example: build an {@code HfcxClient}, construct a tiny FHIR
 * Claim Bundle, post it to the gateway, print the result.
 *
 * <p>Real applications will populate the Bundle from the participant's
 * own data model (HIS / billing system / claim form) rather than the
 * hand-written JSON below.
 */
public final class SubmitClaimExample {

    public static void main(String[] args) {
        String gatewayUrl = required("HFCX_GATEWAY_URL");
        String registryBaseUrl = required("HFCX_REGISTRY_BASE_URL");
        String participantCode = required("HFCX_PARTICIPANT_CODE");
        String recipientCode = required("HFCX_RECIPIENT_CODE");
        String privateKeyPath = required("HFCX_PRIVATE_KEY_PATH");
        String keycloakTokenUrl = required("KEYCLOAK_TOKEN_URL");
        String keycloakClientId = required("KEYCLOAK_CLIENT_ID");
        String keycloakClientSecret = required("KEYCLOAK_CLIENT_SECRET");

        HfcxClient client = HfcxClient.builder()
                .gatewayUrl(gatewayUrl)
                .participantCode(participantCode)
                .privateKeyPath(privateKeyPath)
                .keycloak(KeycloakTokenClient.builder()
                        .tokenEndpoint(keycloakTokenUrl)
                        .clientId(keycloakClientId)
                        .clientSecret(keycloakClientSecret)
                        .build())
                .registryClient(RegistryClient.builder()
                        .registryBaseUrl(registryBaseUrl)
                        .build())
                .build();

        // Minimal Egyptian-IG conformant Bundle. Real applications build
        // this from their domain model.
        String claimBundle = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "resource": {
                        "resourceType": "Patient",
                        "identifier": [{
                          "system": "http://hcx-egypt.gov.eg/identifiers/national-id",
                          "value": "29504150112355"
                        }],
                        "address": [{"country": "EG"}]
                      }
                    }
                  ]
                }
                """;

        try {
            HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                    .recipientCode(recipientCode)
                    .claimBundle(claimBundle)
                    .build());
            System.out.printf("correlationId=%s status=%s%n",
                    response.correlationId(), response.status());
        } catch (BusinessException e) {
            System.err.printf("Recipient or registry rejected the claim: %s (%s)%n",
                    e.getCode(), e.getMessage());
            System.exit(2);
        } catch (HfcxException e) {
            System.err.printf("HFCX error: %s (%s)%n", e.getCode(), e.getMessage());
            System.exit(1);
        }
    }

    private static String required(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            System.err.println("Missing required environment variable: " + envVar);
            System.exit(64);
        }
        return value;
    }

    private SubmitClaimExample() {}
}
