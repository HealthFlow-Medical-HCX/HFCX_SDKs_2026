package eg.gov.healthflow.hfcx.sdk.core.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JweAlgorithmsTest {

    /**
     * Pinning these values is a security invariant: any change here is a
     * cross-SDK breaking change and must land alongside Python, .NET, and
     * JavaScript SDK updates plus a platform-side review.
     */
    @Test
    void algorithmsArePinned() {
        assertEquals("RSA-OAEP-256", JweAlgorithms.ALG);
        assertEquals("A256GCM", JweAlgorithms.ENC);
    }
}
