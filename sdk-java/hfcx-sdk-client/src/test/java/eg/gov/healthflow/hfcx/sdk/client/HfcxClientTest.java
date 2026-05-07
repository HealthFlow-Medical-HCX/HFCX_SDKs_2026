package eg.gov.healthflow.hfcx.sdk.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HfcxClientTest {

    @Test
    void sdkVersionResolvesFromCore() {
        assertNotNull(HfcxClient.sdkVersion(),
                "HfcxClient.sdkVersion() must resolve via hfcx-sdk-core");
    }
}
