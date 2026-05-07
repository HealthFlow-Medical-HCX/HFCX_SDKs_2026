package eg.gov.healthflow.smoketest;

import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;
import eg.gov.healthflow.hfcx.sdk.core.HfcxSdkVersion;
import eg.gov.healthflow.hfcx.sdk.core.crypto.JweAlgorithms;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Confirms that a clean Maven consumer project can resolve and invoke the
 * SDK's public API surface. Sprint J1 acceptance criterion.
 */
class ConsumerSmokeTest {

    @Test
    void resolvesSdkVersionViaPublicApi() {
        String version = HfcxClient.sdkVersion();
        assertNotNull(version);
        assertEquals(HfcxSdkVersion.VERSION, version);
    }

    @Test
    void exposesPinnedJweAlgorithms() {
        assertEquals("RSA-OAEP-256", JweAlgorithms.ALG);
        assertEquals("A256GCM", JweAlgorithms.ENC);
    }

    @Test
    void exposesErrorTaxonomy() {
        BusinessException ex = new BusinessException("ERR-B-006", "smoke");
        assertEquals("ERR-B-006", ex.getCode());
    }
}
