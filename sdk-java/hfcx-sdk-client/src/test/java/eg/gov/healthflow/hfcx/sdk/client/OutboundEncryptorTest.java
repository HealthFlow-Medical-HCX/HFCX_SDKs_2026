package eg.gov.healthflow.hfcx.sdk.client;

import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.registry.RecipientCertResolver;
import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OutboundEncryptor coordinates the registry lookup and the JWE primitive.
 * The integration with each is exercised by {@code RegistryClientTest}
 * and {@code JweEncryptionTest} respectively; this class confirms the
 * composition.
 */
class OutboundEncryptorTest {

    private static TestCerts.GeneratedCert recipient;

    @BeforeAll
    static void generateRecipientCert() throws Exception {
        recipient = TestCerts.generate("payerco@hcx-egypt", Duration.ofDays(3650));
    }

    @Test
    void encryptProducesJweDecryptableByRecipient() {
        OutboundEncryptor encryptor = new OutboundEncryptor(stubResolver(recipient));

        String payload = "{\"resourceType\":\"Bundle\",\"type\":\"collection\"}";
        String jwe = encryptor.encrypt(payload, "payerco@hcx-egypt");

        // Compact form: 5 base64url segments.
        assertEquals(5, jwe.split("\\.").length);
        assertEquals(payload, JweEncryption.decryptUtf8(jwe, recipient.privateKey()));
    }

    @Test
    void distinctEncryptionsProduceDistinctCiphertexts() {
        OutboundEncryptor encryptor = new OutboundEncryptor(stubResolver(recipient));
        String first = encryptor.encrypt("deterministic", "payerco@hcx-egypt");
        String second = encryptor.encrypt("deterministic", "payerco@hcx-egypt");
        assertNotEquals(first, second);
    }

    @Test
    void recipientCodeIsPropagatedToTheResolver() {
        AtomicReference<String> seen = new AtomicReference<>();
        OutboundEncryptor encryptor = new OutboundEncryptor(code -> {
            seen.set(code);
            return new ParticipantCert(code, recipient.publicKey(),
                    Instant.now().plusSeconds(3600));
        });
        encryptor.encrypt("payload", "specific-recipient@hcx-egypt");
        assertEquals("specific-recipient@hcx-egypt", seen.get());
    }

    @Test
    void rejectsNullArguments() {
        OutboundEncryptor encryptor = new OutboundEncryptor(stubResolver(recipient));
        assertThrows(NullPointerException.class, () -> encryptor.encrypt(null, "r"));
        assertThrows(NullPointerException.class, () -> encryptor.encrypt("p", null));
        assertThrows(NullPointerException.class, () -> new OutboundEncryptor(null));
    }

    private static RecipientCertResolver stubResolver(TestCerts.GeneratedCert cert) {
        return code -> new ParticipantCert(
                code, cert.publicKey(), Instant.now().plusSeconds(3600));
    }
}
