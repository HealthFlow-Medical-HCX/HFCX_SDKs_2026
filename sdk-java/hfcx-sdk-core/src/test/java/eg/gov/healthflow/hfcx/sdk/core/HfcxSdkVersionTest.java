package eg.gov.healthflow.hfcx.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HfcxSdkVersionTest {

    @Test
    void versionConstantIsPopulated() {
        assertNotNull(HfcxSdkVersion.VERSION);
        assertTrue(HfcxSdkVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?"),
                "VERSION must look like a semver string, was: " + HfcxSdkVersion.VERSION);
    }

    @Test
    void platformVersionConstantIsPopulated() {
        assertNotNull(HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION);
    }
}
