package eg.gov.healthflow.hfcx.sdk.core.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-SDK invariant: every entry in {@link ErrorCode} has a unique wire
 * code, the wire codes follow the canonical {@code ERR-[PBT]-NNN} format,
 * and the tier-prefix matches the declared tier.
 *
 * <p>Sister tests in the Python / .NET / JavaScript SDKs MUST exercise the
 * same invariants against their own catalogs. Deviation is a cross-SDK
 * conformance failure.
 */
class ErrorCodeCatalogTest {

    private static final Pattern WIRE_FORMAT = Pattern.compile("^ERR-[PBT]-\\d{3}$");

    @Test
    void everyEntryHasAUniqueWireCode() {
        Set<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .collect(Collectors.toSet());
        assertEquals(ErrorCode.values().length, codes.size(),
                "wire codes must be unique across the catalog");
    }

    @Test
    void everyWireCodeMatchesTheCanonicalFormat() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(WIRE_FORMAT.matcher(code.code()).matches(),
                    "code " + code + " has wire format '" + code.code() + "' "
                            + "which does not match ERR-[PBT]-NNN");
        }
    }

    @Test
    void wireCodePrefixMatchesDeclaredTier() {
        for (ErrorCode code : ErrorCode.values()) {
            char prefix = code.code().charAt(4);
            ErrorCode.Tier expected = switch (prefix) {
                case 'P' -> ErrorCode.Tier.PROTOCOL;
                case 'B' -> ErrorCode.Tier.BUSINESS;
                case 'T' -> ErrorCode.Tier.TECHNICAL;
                default -> throw new AssertionError("unknown tier prefix: " + prefix);
            };
            assertEquals(expected, code.tier(),
                    code + " has wire prefix '" + prefix + "' but declared tier " + code.tier());
        }
    }

    @Test
    void everyEntryHasANonEmptyDescription() {
        for (ErrorCode code : ErrorCode.values()) {
            assertFalse(code.description() == null || code.description().isBlank(),
                    code + " has no human-readable description");
        }
    }

    @Test
    void fromWireRoundTrips() {
        for (ErrorCode code : ErrorCode.values()) {
            Optional<ErrorCode> roundtripped = ErrorCode.fromWire(code.code());
            assertTrue(roundtripped.isPresent(), code + " not resolvable by wire code");
            assertSame(code, roundtripped.get());
        }
    }

    @Test
    void fromWireReturnsEmptyForUnknownCode() {
        assertTrue(ErrorCode.fromWire(null).isEmpty());
        assertTrue(ErrorCode.fromWire("").isEmpty());
        assertTrue(ErrorCode.fromWire("ERR-X-999").isEmpty());
        assertTrue(ErrorCode.fromWire("not-a-code").isEmpty());
    }

    @Test
    void factoryProducesTheCorrectTieredException() {
        assertInstanceOf(ProtocolException.class,
                HfcxException.of(ErrorCode.MISSING_HEADER, "msg"));
        assertInstanceOf(BusinessException.class,
                HfcxException.of(ErrorCode.NATIONAL_ID_INVALID, "msg"));
        assertInstanceOf(TechnicalException.class,
                HfcxException.of(ErrorCode.TRANSPORT, "msg"));
    }

    @Test
    void factoryPreservesTheWireCodeOnTheException() {
        for (ErrorCode code : ErrorCode.values()) {
            HfcxException ex = HfcxException.of(code, "msg");
            assertEquals(code.code(), ex.getCode());
        }
    }

    @Test
    void factoryWithCausePreservesIt() {
        Throwable cause = new RuntimeException("root");
        HfcxException ex = HfcxException.of(ErrorCode.TRANSPORT, "boom", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void documentedSetContainsExpectedNumberOfCodesPerTier() {
        // Pinned counts. Adding a code is a deliberate change; this test
        // forces the cross-SDK catalogs to be updated together.
        long protocols = Arrays.stream(ErrorCode.values())
                .filter(c -> c.tier() == ErrorCode.Tier.PROTOCOL).count();
        long business = Arrays.stream(ErrorCode.values())
                .filter(c -> c.tier() == ErrorCode.Tier.BUSINESS).count();
        long technical = Arrays.stream(ErrorCode.values())
                .filter(c -> c.tier() == ErrorCode.Tier.TECHNICAL).count();
        assertEquals(9, protocols, "protocol-tier code count drifted");
        assertEquals(12, business, "business-tier code count drifted");
        assertEquals(6, technical, "technical-tier code count drifted");
    }

    /**
     * For every code in the catalog there is a matching {@code *Exception}
     * subclass under the same package whose {@code CODE} constant equals
     * the wire code. This guard catches the common mistake of adding a
     * catalog entry without its typed subclass (or vice versa).
     */
    @Test
    void everyCatalogEntryHasAMatchingTypedSubclass() throws Exception {
        // Listing rather than scanning to keep the test deterministic
        // (reflective-classpath scans break in shaded jars / Native Image).
        List<Class<? extends HfcxException>> typed = List.of(
                MissingHeaderException.class,
                JweAlgorithmRejectedException.class,
                RecipientCodeMismatchException.class,
                BadUuidException.class,
                BadTimestampException.class,
                TimestampOutOfRangeException.class,
                SenderUnknownException.class,
                BadEnvelopeException.class,
                SignatureVerificationFailedException.class,
                ParticipantNotFoundException.class,
                NotABundleException.class,
                BundleMissingTypeException.class,
                PatientMissingNationalIdException.class,
                PatientNonEgyptianException.class,
                NationalIdInvalidException.class,
                PhoneInvalidException.class,
                IbanInvalidException.class,
                BadFhirJsonException.class,
                EnvelopeMissingPayloadException.class,
                EnvelopeMalformedJsonException.class,
                UnknownBusinessException.class,
                TransportException.class,
                AuthenticationException.class,
                RegistryUnavailableException.class,
                KeyUnavailableException.class,
                CryptographicFailureException.class,
                Gateway5xxException.class);

        Set<String> typedCodes = new HashSet<>();
        for (Class<? extends HfcxException> cls : typed) {
            String code = (String) cls.getField("CODE").get(null);
            typedCodes.add(code);
        }

        Set<String> catalogCodes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(catalogCodes, typedCodes,
                "every ErrorCode must have a matching typed subclass");
        assertEquals(ErrorCode.values().length, typed.size());
    }
}
