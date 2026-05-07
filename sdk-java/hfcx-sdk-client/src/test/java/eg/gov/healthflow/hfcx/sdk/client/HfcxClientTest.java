package eg.gov.healthflow.hfcx.sdk.client;

import eg.gov.healthflow.hfcx.sdk.client.auth.KeycloakTokenClient;
import eg.gov.healthflow.hfcx.sdk.client.request.CheckEligibilityRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.NotifyPaymentRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SendCommunicationRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitClaimRequest;
import eg.gov.healthflow.hfcx.sdk.client.request.SubmitPreauthRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HfcxClientTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private HfcxClient.Builder validBuilder() {
        return HfcxClient.builder()
                .gatewayUrl("https://healthflow.gov.eg")
                .participantCode("myhospital@hcx-egypt")
                .privateKeyPath("/run/secrets/hfcx-private-key.pem")
                .keycloak(stubKeycloak());
    }

    private KeycloakTokenClient stubKeycloak() {
        // The stubs don't actually use the token client today; J4 will.
        // Construct a real instance against a placeholder URL — we never
        // call getToken() in this test class.
        return KeycloakTokenClient.builder()
                .tokenEndpoint("http://placeholder/token")
                .clientId("test-client")
                .clientSecret("test-secret")
                .build();
    }

    @Test
    void builderRequiresAllFields() {
        assertThrows(NullPointerException.class, () -> HfcxClient.builder().build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").participantCode("p").build());
        assertThrows(NullPointerException.class, () -> HfcxClient.builder()
                .gatewayUrl("https://x").participantCode("p").privateKeyPath("/k").build());
    }

    @Test
    void builderAcceptsStringPathAndPathInterchangeably() {
        HfcxClient a = validBuilder().build();
        HfcxClient b = HfcxClient.builder()
                .gatewayUrl("https://x")
                .participantCode("p")
                .privateKeyPath(java.nio.file.Path.of("/run/secrets/k.pem"))
                .keycloak(stubKeycloak())
                .build();
        assertNotNull(a);
        assertNotNull(b);
    }

    @Test
    void submitClaimAutoGeneratesUuid4WhenCallerOmitsCorrelationId() {
        HfcxClient client = validBuilder().build();

        HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{\"resourceType\":\"Bundle\"}")
                .build());

        assertNotNull(response.correlationId());
        assertTrue(UUID_PATTERN.matcher(response.correlationId()).matches(),
                "auto-generated correlation ID must be UUID4: " + response.correlationId());
        assertEquals(Status.STUBBED, response.status());
    }

    @Test
    void callerSuppliedCorrelationIdIsPropagatedUnchanged() {
        HfcxClient client = validBuilder().build();
        String supplied = "11111111-2222-4333-8444-555555555555";

        HfcxResponse response = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{}")
                .correlationId(supplied)
                .build());

        assertEquals(supplied, response.correlationId());
    }

    @Test
    void distinctCallsGenerateDistinctCorrelationIds() {
        HfcxClient client = validBuilder().build();
        SubmitClaimRequest req = SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt")
                .claimBundle("{}")
                .build();

        String first = client.submitClaim(req).correlationId();
        String second = client.submitClaim(req).correlationId();
        assertNotEquals(first, second);
    }

    @Test
    void allFiveSenderMethodsAreReachableAndReturnTypedResponses() {
        HfcxClient client = validBuilder().build();
        String corr = UUID.randomUUID().toString();

        HfcxResponse e = client.checkEligibility(CheckEligibilityRequest.builder()
                .recipientCode("payerco@hcx-egypt").eligibilityBundle("{}").correlationId(corr).build());
        HfcxResponse pa = client.submitPreauth(SubmitPreauthRequest.builder()
                .recipientCode("payerco@hcx-egypt").preauthBundle("{}").correlationId(corr).build());
        HfcxResponse cl = client.submitClaim(SubmitClaimRequest.builder()
                .recipientCode("payerco@hcx-egypt").claimBundle("{}").correlationId(corr).build());
        HfcxResponse co = client.sendCommunication(SendCommunicationRequest.builder()
                .recipientCode("payerco@hcx-egypt").communicationBundle("{}").correlationId(corr).build());
        HfcxResponse pn = client.notifyPayment(NotifyPaymentRequest.builder()
                .recipientCode("payerco@hcx-egypt").paymentNoticeBundle("{}").correlationId(corr).build());

        for (HfcxResponse r : new HfcxResponse[]{e, pa, cl, co, pn}) {
            assertEquals(corr, r.correlationId(),
                    "every method must propagate the caller-supplied correlation ID");
            assertEquals(Status.STUBBED, r.status());
        }
    }

    @Test
    void requestRecordsRejectNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> SubmitClaimRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SubmitClaimRequest.builder()
                .recipientCode("r").build());
        assertThrows(NullPointerException.class, () -> CheckEligibilityRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SubmitPreauthRequest.builder().build());
        assertThrows(NullPointerException.class, () -> SendCommunicationRequest.builder().build());
        assertThrows(NullPointerException.class, () -> NotifyPaymentRequest.builder().build());
    }

    @Test
    void sdkVersionResolvesViaCore() {
        assertNotNull(HfcxClient.sdkVersion());
    }
}
