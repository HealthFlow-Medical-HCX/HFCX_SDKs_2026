package eg.gov.healthflow.hfcx.sdk.client.protocol;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wire-format guard rails for the protocol headers. The exact byte
 * sequence the SDK emits is part of the cross-SDK invariant — Python,
 * .NET, and JavaScript SDKs MUST emit the same names and ordering.
 */
class ProtocolHeadersTest {

    @Test
    void headerNamesMatchIntegrationGuide_24_5_Gap7Form() {
        // Pinned. A change here is a coordinated cross-SDK breaking change.
        assertEquals("x-hcx-sender_code", ProtocolHeaders.SENDER_CODE);
        assertEquals("x-hcx-recipient_code", ProtocolHeaders.RECIPIENT_CODE);
        assertEquals("x-hcx-correlation_id", ProtocolHeaders.CORRELATION_ID);
        assertEquals("x-hcx-timestamp", ProtocolHeaders.TIMESTAMP);
        // Note: api-call-id is fully hyphenated, distinct from the others.
        assertEquals("x-hcx-api-call-id", ProtocolHeaders.API_CALL_ID);
    }

    @Test
    void buildEmitsExpectedKeysInDeterministicOrder() {
        Map<String, String> headers = ProtocolHeaders.build(
                "myhospital@hcx-egypt",
                "payerco@hcx-egypt",
                "11111111-2222-4333-8444-555555555555",
                Instant.parse("2026-05-07T19:42:18Z"),
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

        assertIterableEquals(
                java.util.List.of(
                        "x-hcx-sender_code",
                        "x-hcx-recipient_code",
                        "x-hcx-correlation_id",
                        "x-hcx-timestamp",
                        "x-hcx-api-call-id"),
                new ArrayList<>(headers.keySet()),
                "header order must be stable so cross-SDK byte-level comparisons succeed");
    }

    @Test
    void timestampIsIso8601WithZuluSuffix() {
        Map<String, String> headers = ProtocolHeaders.build(
                "sender@hcx-egypt",
                "recipient@hcx-egypt",
                "corr-1",
                Instant.parse("2026-05-07T19:42:18Z"),
                "call-1");
        assertEquals("2026-05-07T19:42:18Z", headers.get(ProtocolHeaders.TIMESTAMP));
    }

    @Test
    void valuesArePreservedExactly() {
        Map<String, String> headers = ProtocolHeaders.build(
                "myhospital@hcx-egypt",
                "payerco@hcx-egypt",
                "corr-xyz",
                Instant.parse("2026-05-07T19:42:18Z"),
                "call-123");
        assertEquals("myhospital@hcx-egypt", headers.get(ProtocolHeaders.SENDER_CODE));
        assertEquals("payerco@hcx-egypt", headers.get(ProtocolHeaders.RECIPIENT_CODE));
        assertEquals("corr-xyz", headers.get(ProtocolHeaders.CORRELATION_ID));
        assertEquals("call-123", headers.get(ProtocolHeaders.API_CALL_ID));
    }

    @Test
    void resultIsImmutable() {
        Map<String, String> headers = ProtocolHeaders.build(
                "s", "r", "c",
                Instant.parse("2026-05-07T19:42:18Z"),
                "a");
        assertThrows(UnsupportedOperationException.class,
                () -> headers.put("x-hcx-injected", "boom"));
    }

    @Test
    void rejectsNullArguments() {
        Instant t = Instant.parse("2026-05-07T19:42:18Z");
        assertThrows(NullPointerException.class, () -> ProtocolHeaders.build(null, "r", "c", t, "a"));
        assertThrows(NullPointerException.class, () -> ProtocolHeaders.build("s", null, "c", t, "a"));
        assertThrows(NullPointerException.class, () -> ProtocolHeaders.build("s", "r", null, t, "a"));
        assertThrows(NullPointerException.class, () -> ProtocolHeaders.build("s", "r", "c", null, "a"));
        assertThrows(NullPointerException.class, () -> ProtocolHeaders.build("s", "r", "c", t, null));
    }
}
