package eg.gov.healthflow.hfcx.sdk.core.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import eg.gov.healthflow.hfcx.sdk.core.exception.CryptographicFailureException;
import eg.gov.healthflow.hfcx.sdk.core.exception.JweAlgorithmRejectedException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Objects;

/**
 * JWE compact-form encryption / decryption with hard-pinned algorithms.
 *
 * <p>The HFCX protocol mandates {@code RSA-OAEP-256} for key wrap and
 * {@code A256GCM} for content encryption (see
 * {@link JweAlgorithms}). This class enforces the pin on BOTH sides:
 *
 * <ul>
 *   <li>Encrypt: the constructed {@link JWEHeader} hard-codes the
 *       algorithm pair; nothing the caller passes can change it.</li>
 *   <li>Decrypt: the protected header is parsed and validated BEFORE
 *       any cryptographic operation is performed. A payload claiming
 *       {@code alg=RSA1_5}, {@code alg=none}, or any other unsupported
 *       combination is rejected without touching the recipient's
 *       private key. This is the cross-SDK-invariant downgrade-attack
 *       guard that mirrors the platform's {@code JWEHelper}.</li>
 * </ul>
 *
 * <p>This class is intentionally a low-level primitive: it knows nothing
 * about FHIR, the HFCX gateway, or the registry. The high-level
 * {@code OutboundEncryptor} composes this with the registry lookup; the
 * recipient-side {@code InboundDecryptor} (Sprint J5) composes this with
 * the local key provider.
 */
public final class JweEncryption {

    private JweEncryption() {
        // Static helper.
    }

    /**
     * Encrypt {@code payload} for the holder of {@code recipientPublicKey}.
     *
     * @return JWE compact serialization (5 base64url-encoded parts joined by '.').
     * @throws TechnicalException if the underlying JOSE library reports an error.
     */
    public static String encrypt(byte[] payload, RSAPublicKey recipientPublicKey) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(recipientPublicKey, "recipientPublicKey");

        JWEHeader header = new JWEHeader.Builder(
                JWEAlgorithm.RSA_OAEP_256,
                EncryptionMethod.A256GCM)
                .build();
        JWEObject jwe = new JWEObject(header, new Payload(payload));
        try {
            jwe.encrypt(new RSAEncrypter(recipientPublicKey));
        } catch (JOSEException e) {
            throw new CryptographicFailureException(
                    "JWE encryption failed: " + e.getMessage(), e);
        }
        return jwe.serialize();
    }

    /** Convenience overload: encrypts a UTF-8 string payload. */
    public static String encryptUtf8(String payload, RSAPublicKey recipientPublicKey) {
        Objects.requireNonNull(payload, "payload");
        return encrypt(payload.getBytes(StandardCharsets.UTF_8), recipientPublicKey);
    }

    /**
     * Decrypt the JWE compact serialization with the recipient's private key.
     *
     * @throws ProtocolException if the protected header advertises any
     *     algorithm pair other than {@code RSA-OAEP-256 + A256GCM}.
     * @throws TechnicalException if the underlying JOSE library reports
     *     a parse error, MAC failure, or other cryptographic failure.
     */
    public static byte[] decrypt(String jweCompact, RSAPrivateKey recipientPrivateKey) {
        Objects.requireNonNull(jweCompact, "jweCompact");
        Objects.requireNonNull(recipientPrivateKey, "recipientPrivateKey");

        JWEObject jwe;
        try {
            jwe = JWEObject.parse(jweCompact);
        } catch (ParseException e) {
            throw new CryptographicFailureException(
                    "JWE compact serialization is malformed: " + e.getMessage(), e);
        }

        JWEHeader header = jwe.getHeader();
        if (!JWEAlgorithm.RSA_OAEP_256.equals(header.getAlgorithm())) {
            throw new JweAlgorithmRejectedException(
                    "JWE alg " + header.getAlgorithm()
                            + " rejected; only " + JweAlgorithms.ALG + " is permitted");
        }
        if (!EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())) {
            throw new JweAlgorithmRejectedException(
                    "JWE enc " + header.getEncryptionMethod()
                            + " rejected; only " + JweAlgorithms.ENC + " is permitted");
        }

        try {
            jwe.decrypt(new RSADecrypter(recipientPrivateKey));
        } catch (JOSEException e) {
            throw new CryptographicFailureException(
                    "JWE decryption failed: " + e.getMessage(), e);
        }
        return jwe.getPayload().toBytes();
    }

    /** Convenience overload: decrypts and returns the payload as UTF-8. */
    public static String decryptUtf8(String jweCompact, RSAPrivateKey recipientPrivateKey) {
        return new String(decrypt(jweCompact, recipientPrivateKey), StandardCharsets.UTF_8);
    }
}
