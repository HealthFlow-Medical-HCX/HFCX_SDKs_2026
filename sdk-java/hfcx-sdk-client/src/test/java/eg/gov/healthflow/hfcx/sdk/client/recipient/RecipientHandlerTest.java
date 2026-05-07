package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.client.OutboundEncryptor;
import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientHandlerTest {

    private static TestCerts.GeneratedCert recipient;
    private static OutboundEncryptor outboundEncryptor;

    @BeforeAll
    static void setUp() throws Exception {
        recipient = TestCerts.generate("payerco@hcx-egypt", Duration.ofDays(30));
        outboundEncryptor = new OutboundEncryptor(code -> new ParticipantCert(
                code, recipient.publicKey(), Instant.now().plusSeconds(3600)));
    }

    private RecipientHandler.Builder validBuilder() {
        return RecipientHandler.builder()
                .keyProvider(() -> recipient.privateKey())
                .localParticipantCode("payerco@hcx-egypt")
                .bearerTokenValidator(authHeader -> {
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new AuthenticationException("missing or malformed bearer");
                    }
                });
    }

    private static String envelope(String payload) {
        String jwe = outboundEncryptor.encrypt(payload, "payerco@hcx-egypt");
        return "{\"payload\":\"" + jwe + "\"}";
    }

    private static String validBundle() {
        return "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"" + FhirValidator.NATIONAL_ID_SYSTEM
                + "\",\"value\":\"29504150112355\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
    }

    private static Map<String, String> validHeaders(String correlationId, Instant now) {
        return Map.of(
                ProtocolHeaders.SENDER_CODE, "myhospital@hcx-egypt",
                ProtocolHeaders.RECIPIENT_CODE, "payerco@hcx-egypt",
                ProtocolHeaders.CORRELATION_ID, correlationId,
                ProtocolHeaders.TIMESTAMP, now.toString(),
                ProtocolHeaders.API_CALL_ID, UUID.randomUUID().toString());
    }

    @Test
    void endToEndRoundTripFromOutboundEncryptorYieldsTheOriginalPayload() {
        RecipientHandler handler = validBuilder().build();
        String correlationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String bundle = validBundle();

        RecipientResult result = handler.handle(
                "Bearer test-token",
                validHeaders(correlationId, now),
                envelope(bundle));

        assertEquals(bundle, result.decryptedPayload());
        assertEquals(correlationId, result.correlationId());
    }

    @Test
    void missingBearerHeaderRaisesAuthenticationException() {
        RecipientHandler handler = validBuilder().build();
        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                handler.handle(null, validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        envelope(validBundle())));
        assertEquals("ERR-T-002", ex.getCode());
    }

    @Test
    void disablingBearerLayerSkipsValidatorRequirement() {
        // Build with no validator AND disable the layer — should succeed.
        RecipientHandler handler = RecipientHandler.builder()
                .keyProvider(() -> recipient.privateKey())
                .localParticipantCode("payerco@hcx-egypt")
                .enable(Layer.BEARER, false)
                .build();
        RecipientResult result = handler.handle(null,
                validHeaders(UUID.randomUUID().toString(), Instant.now()),
                envelope(validBundle()));
        assertTrue(result.decryptedPayload().contains("Bundle"));
    }

    @Test
    void enablingBearerWithoutValidatorFailsAtConstruction() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                RecipientHandler.builder()
                        .keyProvider(() -> recipient.privateKey())
                        .localParticipantCode("payerco@hcx-egypt")
                        .build());
        assertTrue(ex.getMessage().contains("BearerTokenValidator"));
    }

    @Test
    void recipientCodeMismatchRaisesProtocolException() {
        RecipientHandler handler = validBuilder().build();
        Map<String, String> headers = Map.of(
                ProtocolHeaders.SENDER_CODE, "myhospital@hcx-egypt",
                ProtocolHeaders.RECIPIENT_CODE, "wrong-recipient@hcx-egypt",
                ProtocolHeaders.CORRELATION_ID, UUID.randomUUID().toString(),
                ProtocolHeaders.TIMESTAMP, Instant.now().toString(),
                ProtocolHeaders.API_CALL_ID, UUID.randomUUID().toString());
        ProtocolException ex = assertThrows(ProtocolException.class, () ->
                handler.handle("Bearer x", headers, envelope(validBundle())));
        assertEquals(HeaderValidator.CODE_RECIPIENT_MISMATCH, ex.getCode());
    }

    @Test
    void missingProtocolHeaderRaisesProtocolException() {
        RecipientHandler handler = validBuilder().build();
        Map<String, String> headers = Map.of(
                ProtocolHeaders.SENDER_CODE, "myhospital@hcx-egypt",
                // recipient_code missing
                ProtocolHeaders.CORRELATION_ID, UUID.randomUUID().toString(),
                ProtocolHeaders.TIMESTAMP, Instant.now().toString(),
                ProtocolHeaders.API_CALL_ID, UUID.randomUUID().toString());
        ProtocolException ex = assertThrows(ProtocolException.class, () ->
                handler.handle("Bearer x", headers, envelope(validBundle())));
        assertEquals(HeaderValidator.CODE_MISSING_HEADER, ex.getCode());
    }

    @Test
    void timestampOutsideToleranceRaisesProtocolException() {
        // Pin clock so the timestamp delta is deterministic.
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-07T12:00:00Z"), ZoneOffset.UTC);
        RecipientHandler handler = validBuilder().clock(fixedClock).build();
        Map<String, String> headers = validHeaders(UUID.randomUUID().toString(),
                Instant.parse("2026-05-07T11:50:00Z"));  // 10min in the past, > 5min tolerance
        ProtocolException ex = assertThrows(ProtocolException.class, () ->
                handler.handle("Bearer x", headers, envelope(validBundle())));
        assertEquals(HeaderValidator.CODE_TIMESTAMP_OUT_OF_RANGE, ex.getCode());
    }

    @Test
    void disablingHeadersLayerSkipsHeaderChecks() {
        RecipientHandler handler = validBuilder().enable(Layer.HEADERS, false).build();
        // Wrong recipient code: would normally raise; disabled layer means accepted.
        Map<String, String> headers = Map.of(
                ProtocolHeaders.SENDER_CODE, "myhospital@hcx-egypt",
                ProtocolHeaders.RECIPIENT_CODE, "wrong@hcx-egypt",
                ProtocolHeaders.CORRELATION_ID, UUID.randomUUID().toString(),
                ProtocolHeaders.TIMESTAMP, Instant.now().toString(),
                ProtocolHeaders.API_CALL_ID, UUID.randomUUID().toString());
        RecipientResult result = handler.handle("Bearer x", headers, envelope(validBundle()));
        assertTrue(result.decryptedPayload().contains("Patient"));
    }

    @Test
    void fhirLayerRejectsNonBundleResource() {
        RecipientHandler handler = validBuilder().build();
        String body = envelope("{\"resourceType\":\"Patient\"}");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()), body));
        assertEquals(FhirValidator.CODE_NOT_A_BUNDLE, ex.getCode());
    }

    @Test
    void fhirLayerRejectsPatientWithoutNationalIdIdentifier() {
        RecipientHandler handler = validBuilder().build();
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"http://other.example.com/id\",\"value\":\"x\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        envelope(bundle)));
        assertEquals(FhirValidator.CODE_PATIENT_MISSING_NATIONAL_ID, ex.getCode());
    }

    @Test
    void fhirLayerRejectsNonEgyptianAddress() {
        RecipientHandler handler = validBuilder().build();
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"" + FhirValidator.NATIONAL_ID_SYSTEM
                + "\",\"value\":\"29504150112355\"}],"
                + "\"address\":[{\"country\":\"US\"}]}}]}";
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        envelope(bundle)));
        assertEquals(FhirValidator.CODE_PATIENT_NON_EGYPTIAN, ex.getCode());
    }

    @Test
    void disablingFhirLayerSkipsStructuralValidation() {
        RecipientHandler handler = validBuilder().enable(Layer.FHIR, false).build();
        // Not even a Bundle — would normally be rejected.
        RecipientResult result = handler.handle("Bearer x",
                validHeaders(UUID.randomUUID().toString(), Instant.now()),
                envelope("{\"resourceType\":\"Patient\"}"));
        assertTrue(result.decryptedPayload().contains("Patient"));
    }

    @Test
    void egyptianLayerRejectsInvalidNationalIdValue() {
        RecipientHandler handler = validBuilder().build();
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"" + FhirValidator.NATIONAL_ID_SYSTEM
                + "\",\"value\":\"00000000000000\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        envelope(bundle)));
        assertEquals(EgyptianBundleValidator.CODE_BAD_NATIONAL_ID, ex.getCode());
    }

    @Test
    void egyptianLayerRejectsInvalidPhoneValue() {
        RecipientHandler handler = validBuilder().build();
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"" + FhirValidator.NATIONAL_ID_SYSTEM
                + "\",\"value\":\"29504150112355\"}],"
                + "\"telecom\":[{\"system\":\"phone\",\"value\":\"01312345678\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        envelope(bundle)));
        assertEquals(EgyptianBundleValidator.CODE_BAD_PHONE, ex.getCode());
    }

    @Test
    void disablingEgyptianLayerSkipsValueValidation() {
        RecipientHandler handler = validBuilder().enable(Layer.EGYPTIAN, false).build();
        String bundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\","
                + "\"identifier\":[{\"system\":\"" + FhirValidator.NATIONAL_ID_SYSTEM
                + "\",\"value\":\"00000000000000\"}],"
                + "\"address\":[{\"country\":\"EG\"}]}}]}";
        // Invalid National-ID — would normally raise; layer disabled means accepted.
        RecipientResult result = handler.handle("Bearer x",
                validHeaders(UUID.randomUUID().toString(), Instant.now()),
                envelope(bundle));
        assertTrue(result.decryptedPayload().contains("00000000000000"));
    }

    @Test
    void allLayersDisabledStillDecrypts() {
        RecipientHandler handler = RecipientHandler.builder()
                .keyProvider(() -> recipient.privateKey())
                .enable(Layer.BEARER, false)
                .enable(Layer.HEADERS, false)
                .enable(Layer.FHIR, false)
                .enable(Layer.EGYPTIAN, false)
                .build();
        RecipientResult result = handler.handle(null,
                Map.of(ProtocolHeaders.CORRELATION_ID, "x"),
                envelope("plain string payload"));
        assertEquals("plain string payload", result.decryptedPayload());
    }

    @Test
    void enabledLayersAccessorReflectsBuilderToggles() {
        RecipientHandler handler = validBuilder()
                .enable(Layer.FHIR, false)
                .enable(Layer.EGYPTIAN, false)
                .build();
        assertTrue(handler.enabledLayers().contains(Layer.BEARER));
        assertTrue(handler.enabledLayers().contains(Layer.HEADERS));
        assertEquals(2, handler.enabledLayers().size());
    }

    @Test
    void envelopeMissingPayloadFieldRaisesBusinessException() {
        RecipientHandler handler = validBuilder().build();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        "{\"other\":\"x\"}"));
        assertEquals("ERR-B-ENV-001", ex.getCode());
    }

    @Test
    void envelopeMalformedJsonRaisesBusinessException() {
        RecipientHandler handler = validBuilder().build();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                handler.handle("Bearer x",
                        validHeaders(UUID.randomUUID().toString(), Instant.now()),
                        "not json"));
        assertEquals("ERR-B-ENV-002", ex.getCode());
    }
}
