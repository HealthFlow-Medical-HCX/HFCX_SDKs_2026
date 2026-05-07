package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption;

import java.util.Objects;

/**
 * Composes the {@link LocalKeyProvider} and the
 * {@link JweEncryption#decryptUtf8(String, java.security.interfaces.RSAPrivateKey)}
 * primitive to recover the plaintext FHIR payload from a JWE compact
 * serialization.
 *
 * <p>Cross-SDK parity row: {@code InboundDecryptor.decrypt}.
 */
public final class InboundDecryptor {

    private final LocalKeyProvider keyProvider;

    public InboundDecryptor(LocalKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
    }

    /**
     * @return the decrypted UTF-8 payload (typically a FHIR Bundle as JSON).
     * @throws eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException
     *     if the JWE protected header advertises any algorithm pair other
     *     than the pinned {@code RSA-OAEP-256 + A256GCM}.
     */
    public String decrypt(String jweCompact) {
        Objects.requireNonNull(jweCompact, "jweCompact");
        return JweEncryption.decryptUtf8(jweCompact, keyProvider.getPrivateKey());
    }
}
