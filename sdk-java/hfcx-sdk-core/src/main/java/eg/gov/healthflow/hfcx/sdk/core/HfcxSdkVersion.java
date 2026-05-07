package eg.gov.healthflow.hfcx.sdk.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Build-resolved version metadata for the HFCX SDK.
 *
 * <p>Values are loaded from {@code version.properties} on the classpath,
 * which is filtered at build time by {@code maven-resources-plugin} from
 * the {@code project.version} and {@code platform.version} POM properties.
 * Surfaced so {@code HfcxClient} can include the SDK version in
 * {@code User-Agent} headers and structured logs.
 */
public final class HfcxSdkVersion {

    private static final String RESOURCE_PATH =
            "/eg/gov/healthflow/hfcx/sdk/core/version.properties";

    /**
     * Semver string of the SDK artifact, e.g. {@code "1.0.0-SNAPSHOT"} during
     * development or {@code "1.0.0"} for a GA build.
     */
    public static final String VERSION;

    /**
     * Version of the HFCX platform release this SDK was built and
     * integration-tested against. Updated by {@code fhir-ig/sync.sh}
     * which rewrites both {@code fhir-ig/PLATFORM_VERSION} and the
     * {@code <platform.version>} property in the parent POM.
     */
    public static final String COMPATIBLE_PLATFORM_VERSION;

    static {
        Properties props = new Properties();
        try (InputStream in = HfcxSdkVersion.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "version.properties missing from classpath at " + RESOURCE_PATH
                                + " — Maven resource filtering is misconfigured");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load " + RESOURCE_PATH, e);
        }
        VERSION = require(props, "version");
        COMPATIBLE_PLATFORM_VERSION = require(props, "platform.version");
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(
                    "version.properties did not resolve key '" + key
                            + "' — Maven filtering produced literal '" + value + "'");
        }
        return value;
    }

    private HfcxSdkVersion() {
        // Utility holder; no instances.
    }
}
