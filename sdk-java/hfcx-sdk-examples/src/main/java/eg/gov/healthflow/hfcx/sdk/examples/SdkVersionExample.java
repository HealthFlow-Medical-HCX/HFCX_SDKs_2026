package eg.gov.healthflow.hfcx.sdk.examples;

import eg.gov.healthflow.hfcx.sdk.client.HfcxClient;

/**
 * Smallest possible reachability check: confirms the examples module can
 * resolve the {@code hfcx-sdk-client} artifact at compile time and at run
 * time. Replaced in Sprint J7 with full sender/recipient examples.
 */
public final class SdkVersionExample {

    private SdkVersionExample() {
        // Entry point only.
    }

    public static void main(String[] args) {
        System.out.println("HFCX SDK for Java version: " + HfcxClient.sdkVersion());
    }
}
