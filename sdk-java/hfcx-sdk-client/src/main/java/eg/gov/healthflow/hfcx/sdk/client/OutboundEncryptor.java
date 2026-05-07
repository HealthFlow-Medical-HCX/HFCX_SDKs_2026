package eg.gov.healthflow.hfcx.sdk.client;

import eg.gov.healthflow.hfcx.sdk.client.registry.ParticipantCert;
import eg.gov.healthflow.hfcx.sdk.client.registry.RecipientCertResolver;
import eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption;

import java.util.Objects;

/**
 * Composes the registry lookup with the JWE primitive: given a recipient
 * participant code and a payload (typically a FHIR Bundle as JSON), it
 * fetches the recipient's encryption cert from the registry and returns
 * a JWE compact serialization ready to drop into an HFCX request body.
 *
 * <p>The cross-SDK parity table names this {@code OutboundEncryptor.encrypt}
 * in every SDK; the equivalent in Python is {@code crypto.encrypt}.
 */
public final class OutboundEncryptor {

    private final RecipientCertResolver certResolver;

    public OutboundEncryptor(RecipientCertResolver certResolver) {
        this.certResolver = Objects.requireNonNull(certResolver, "certResolver");
    }

    /**
     * Encrypt {@code payload} for the holder of {@code recipientCode}'s
     * registered encryption key.
     *
     * @return JWE compact serialization (5 base64url segments)
     */
    public String encrypt(String payload, String recipientCode) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(recipientCode, "recipientCode");
        ParticipantCert cert = certResolver.getRecipientCert(recipientCode);
        return JweEncryption.encryptUtf8(payload, cert.publicKey());
    }
}
