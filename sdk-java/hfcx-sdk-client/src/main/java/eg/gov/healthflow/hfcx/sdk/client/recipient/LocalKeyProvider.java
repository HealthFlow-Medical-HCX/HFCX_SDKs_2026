package eg.gov.healthflow.hfcx.sdk.client.recipient;

import java.security.interfaces.RSAPrivateKey;

/**
 * Source of the recipient's RSA private key. Pluggable so participants
 * can ship their own implementations against AWS KMS, Azure Key Vault,
 * an HSM, etc. The SDK ships two reference implementations:
 *
 * <ul>
 *   <li>{@link FileLocalKeyProvider} — reads PEM from a filesystem path.</li>
 *   <li>{@link VaultLocalKeyProvider} — reads from HashiCorp Vault KV v2.</li>
 * </ul>
 *
 * <p>Implementations MUST NOT cache the key bytes on disk. The
 * {@link RSAPrivateKey} object is the only material that should ever
 * leave this interface.
 */
@FunctionalInterface
public interface LocalKeyProvider {

    /**
     * @return the recipient's current RSA private key.
     */
    RSAPrivateKey getPrivateKey();
}
