package eg.gov.healthflow.hfcx.sdk.core.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JweEncryptionTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
    }

    @Test
    void roundTripPreservesPayloadBytes() {
        byte[] payload = "{\"resourceType\":\"Bundle\",\"type\":\"collection\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String compact = JweEncryption.encrypt(payload, publicKey);

        // Compact form is 5 base64url segments separated by '.'.
        assertEquals(5, compact.split("\\.").length, "compact form must have 5 segments");

        byte[] decrypted = JweEncryption.decrypt(compact, privateKey);
        assertArrayEquals(payload, decrypted);
    }

    @Test
    void utf8RoundTrip() {
        String payload = "Hello, إجاد الإسلامية! 𝕮"; // mix of ASCII, Arabic, supplementary plane
        String decrypted = JweEncryption.decryptUtf8(
                JweEncryption.encryptUtf8(payload, publicKey),
                privateKey);
        assertEquals(payload, decrypted);
    }

    @Test
    void distinctEncryptionsOfTheSamePayloadProduceDistinctCiphertexts() {
        // GCM with a fresh CEK + IV per encryption — same input must
        // never produce the same compact serialization twice.
        byte[] payload = "deterministic-input".getBytes();
        String first = JweEncryption.encrypt(payload, publicKey);
        String second = JweEncryption.encrypt(payload, publicKey);
        assertNotEquals(first, second);
    }

    @Test
    void downgradeAttempt_RSA1_5_isRejectedBeforeDecryption() throws Exception {
        // Construct a JWE with the legacy (PKCS#1 v1.5) key-wrap algorithm.
        // It would be cryptographically valid for the holder of our private
        // key, but the SDK MUST reject it on header inspection alone.
        String legacy = buildJweWith(JWEAlgorithm.RSA1_5, EncryptionMethod.A256GCM);
        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> JweEncryption.decrypt(legacy, privateKey));
        assertEquals("ERR-P-002", ex.getCode());
        assertTrue(ex.getMessage().contains("RSA1_5"));
    }

    @Test
    void downgradeAttempt_A128GCM_isRejectedBeforeDecryption() throws Exception {
        String weakerEnc = buildJweWith(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM);
        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> JweEncryption.decrypt(weakerEnc, privateKey));
        assertEquals("ERR-P-002", ex.getCode());
        assertTrue(ex.getMessage().contains("A128GCM"));
    }

    @Test
    void downgradeAttempt_A256CBC_HS512_isRejectedBeforeDecryption() throws Exception {
        String cbcMode = buildJweWith(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256CBC_HS512);
        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> JweEncryption.decrypt(cbcMode, privateKey));
        assertEquals("ERR-P-002", ex.getCode());
    }

    @Test
    void downgradeAttempt_RSA_OAEP_isRejectedBeforeDecryption() throws Exception {
        // Bare RSA-OAEP (SHA-1) is a downgrade from RSA-OAEP-256 (SHA-256).
        String sha1 = buildJweWith(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A256GCM);
        ProtocolException ex = assertThrows(ProtocolException.class,
                () -> JweEncryption.decrypt(sha1, privateKey));
        assertEquals("ERR-P-002", ex.getCode());
    }

    @Test
    void malformedCompactSerializationIsRejected() {
        assertThrows(TechnicalException.class,
                () -> JweEncryption.decrypt("not.a.real.jwe", privateKey));
        assertThrows(TechnicalException.class,
                () -> JweEncryption.decrypt("garbage", privateKey));
    }

    private static String buildJweWith(JWEAlgorithm alg, EncryptionMethod enc) throws Exception {
        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(alg, enc).build(),
                new Payload("downgrade-payload"));
        jwe.encrypt(new RSAEncrypter(publicKey));
        return jwe.serialize();
    }
}
