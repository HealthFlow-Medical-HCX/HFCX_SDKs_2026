package eg.gov.healthflow.hfcx.sdk.core;

/**
 * Resolved at build time from the parent POM's {@code project.version}.
 * Surfaced so {@code HfcxClient} can include the SDK version in
 * {@code User-Agent} headers and structured logs.
 */
public final class HfcxSdkVersion {

    /**
     * Semver string of the SDK artifact, e.g. {@code "1.0.0-SNAPSHOT"} during
     * development or {@code "1.0.0"} for a GA build.
     */
    public static final String VERSION = "1.0.0-SNAPSHOT";

    /**
     * Version of the HFCX platform release this SDK was built and
     * integration-tested against. Updated by {@code fhir-ig/sync.sh}.
     */
    public static final String COMPATIBLE_PLATFORM_VERSION = "unbundled";

    private HfcxSdkVersion() {
        // Utility holder; no instances.
    }
}
