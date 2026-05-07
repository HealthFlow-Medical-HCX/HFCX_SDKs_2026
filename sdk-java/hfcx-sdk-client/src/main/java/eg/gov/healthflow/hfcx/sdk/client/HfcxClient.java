package eg.gov.healthflow.hfcx.sdk.client;

import eg.gov.healthflow.hfcx.sdk.core.HfcxSdkVersion;

/**
 * High-level entry point for HFCX participants acting as senders
 * (providers, payers initiating preauths, etc.).
 *
 * <p>Sprint J1 ships this class as a stub so downstream consumers can
 * resolve the artifact and verify wiring. Sprints J3–J6 fill in the
 * real builder, the five protocol methods (eligibility, preauth, claim,
 * communication, payment notice), and the error mapping.
 */
public final class HfcxClient {

    private HfcxClient() {
        // Sprint J3 introduces a builder.
    }

    /**
     * @return the SDK version this client was compiled against, exposed for
     *     User-Agent header construction in later sprints.
     */
    public static String sdkVersion() {
        return HfcxSdkVersion.VERSION;
    }
}
