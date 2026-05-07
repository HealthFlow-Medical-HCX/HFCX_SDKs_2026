package eg.gov.healthflow.hfcx.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HfcxSdkVersionTest {

    @Test
    void versionMatchesSemver() {
        assertNotNull(HfcxSdkVersion.VERSION);
        assertTrue(HfcxSdkVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?"),
                "VERSION must look like a semver string, was: " + HfcxSdkVersion.VERSION);
    }

    /**
     * Guards against a regression where the static initializer accidentally
     * loads an unfiltered template (e.g. resource filtering disabled) and
     * exposes literal {@code ${project.version}} placeholders to callers.
     */
    @Test
    void versionIsResolvedNotALiteralPlaceholder() {
        assertFalse(HfcxSdkVersion.VERSION.contains("${"),
                "VERSION still contains a Maven placeholder: " + HfcxSdkVersion.VERSION);
    }

    @Test
    void platformVersionIsResolvedNotALiteralPlaceholder() {
        assertNotNull(HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION);
        assertFalse(HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION.contains("${"),
                "COMPATIBLE_PLATFORM_VERSION still contains a Maven placeholder: "
                        + HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION);
    }
}
