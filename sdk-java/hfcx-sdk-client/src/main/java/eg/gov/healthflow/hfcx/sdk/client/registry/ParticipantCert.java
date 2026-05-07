package eg.gov.healthflow.hfcx.sdk.client.registry;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * Cached lookup result for a single HFCX participant: the recipient's
 * public encryption key plus the X.509 cert's {@code notAfter} so the
 * cache can expire entries before the cert itself does.
 *
 * @param participantCode HFCX participant code, e.g. {@code "payerco@hcx-egypt"}
 * @param publicKey       RSA public key extracted from the participant's encryption cert
 * @param notAfter        cert {@code notAfter} value; the cache TTL is set to
 *                        {@code notAfter - 1h} so a request never goes out
 *                        with a key that the gateway is about to reject as expired
 */
public record ParticipantCert(String participantCode, RSAPublicKey publicKey, Instant notAfter) {

    public ParticipantCert {
        Objects.requireNonNull(participantCode, "participantCode");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(notAfter, "notAfter");
    }
}
