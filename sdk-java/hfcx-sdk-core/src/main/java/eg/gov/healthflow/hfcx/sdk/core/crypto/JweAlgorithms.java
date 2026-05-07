package eg.gov.healthflow.hfcx.sdk.core.crypto;

/**
 * Hard-pinned JWE algorithm identifiers required by the HFCX protocol.
 *
 * <p>Both Sprint J4 (outbound encryption) and Sprint J5 (inbound decryption)
 * MUST reject any payload whose protected header advertises algorithms other
 * than the values declared here. This mirrors the platform's
 * {@code JWEHelper} downgrade-attack protection.
 *
 * <p>Allowed combinations:
 * <ul>
 *   <li>Key wrap: {@value #ALG}</li>
 *   <li>Content encryption: {@value #ENC}</li>
 * </ul>
 */
public final class JweAlgorithms {

    public static final String ALG = "RSA-OAEP-256";
    public static final String ENC = "A256GCM";

    private JweAlgorithms() {
        // Constants holder; no instances.
    }
}
