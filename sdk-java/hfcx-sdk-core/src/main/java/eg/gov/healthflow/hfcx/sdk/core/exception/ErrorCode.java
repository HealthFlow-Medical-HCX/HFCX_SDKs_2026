package eg.gov.healthflow.hfcx.sdk.core.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single source of truth for every error code the SDK raises.
 *
 * <p>Each entry pins a wire-format code (e.g. {@code "ERR-P-001"}), a
 * {@link Tier} indicating which abstract exception class the code maps
 * to, and a human-readable description. Cross-SDK invariant: Python /
 * .NET / JavaScript SDKs MUST expose the same set of wire-format codes
 * with the same tier mapping, idiomatic case differences aside.
 *
 * <p>When the platform's {@code ErrorCodes.java} grows a new code, this
 * enum gains a new entry and the matching typed subclass under the
 * {@code exception} package. Removing an entry is a breaking change
 * that bumps the SDK's major version in lockstep with the platform.
 */
public enum ErrorCode {

    // ── ERR-P-* — protocol / wire-format violations ─────────────────────
    MISSING_HEADER("ERR-P-001", Tier.PROTOCOL, "Required protocol header missing or empty"),
    JWE_ALGORITHM_REJECTED("ERR-P-002", Tier.PROTOCOL, "JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM"),
    RECIPIENT_CODE_MISMATCH("ERR-P-003", Tier.PROTOCOL, "x-hcx-recipient_code does not match this participant"),
    BAD_UUID("ERR-P-004", Tier.PROTOCOL, "Header value is not a valid UUID"),
    BAD_TIMESTAMP("ERR-P-005", Tier.PROTOCOL, "x-hcx-timestamp is not a valid ISO-8601 instant"),
    TIMESTAMP_OUT_OF_RANGE("ERR-P-006", Tier.PROTOCOL, "x-hcx-timestamp is outside the configured tolerance window"),
    SENDER_UNKNOWN("ERR-P-007", Tier.PROTOCOL, "Sender participant code is not registered"),
    BAD_ENVELOPE("ERR-P-008", Tier.PROTOCOL, "Request body envelope is malformed"),
    SIGNATURE_VERIFICATION_FAILED("ERR-P-009", Tier.PROTOCOL, "Detached signature verification failed"),

    // ── ERR-B-* — business / FHIR / Egyptian profile violations ─────────
    PARTICIPANT_NOT_FOUND("ERR-B-001", Tier.BUSINESS, "Participant code not found in the registry"),
    NOT_A_BUNDLE("ERR-B-002", Tier.BUSINESS, "Top-level FHIR resource is not a Bundle"),
    BUNDLE_MISSING_TYPE("ERR-B-003", Tier.BUSINESS, "Bundle.type is required by the Egyptian IG"),
    PATIENT_MISSING_NATIONAL_ID("ERR-B-004", Tier.BUSINESS, "Patient resource missing the National-ID identifier slice"),
    PATIENT_NON_EGYPTIAN("ERR-B-005", Tier.BUSINESS, "Patient.address[0].country must be 'EG'"),
    NATIONAL_ID_INVALID("ERR-B-006", Tier.BUSINESS, "Egyptian National ID value fails structural validation"),
    PHONE_INVALID("ERR-B-007", Tier.BUSINESS, "Egyptian mobile phone value is not in a recognised format"),
    IBAN_INVALID("ERR-B-008", Tier.BUSINESS, "Egyptian IBAN value fails the ISO 13616 mod-97 check"),
    BAD_FHIR_JSON("ERR-B-009", Tier.BUSINESS, "FHIR payload is not valid JSON"),
    ENVELOPE_MISSING_PAYLOAD("ERR-B-010", Tier.BUSINESS, "Request body envelope is missing the 'payload' field"),
    ENVELOPE_MALFORMED_JSON("ERR-B-011", Tier.BUSINESS, "Request body is not valid JSON"),
    UNKNOWN_BUSINESS("ERR-B-012", Tier.BUSINESS, "Unspecified business-rule failure (gateway error code missing or unparseable)"),

    // ── ERR-T-* — technical / transport failures ────────────────────────
    TRANSPORT("ERR-T-001", Tier.TECHNICAL, "Transport-layer failure"),
    AUTHENTICATION("ERR-T-002", Tier.TECHNICAL, "Authentication rejected by the identity provider"),
    REGISTRY_UNAVAILABLE("ERR-T-003", Tier.TECHNICAL, "Participant registry is unreachable"),
    KEY_UNAVAILABLE("ERR-T-004", Tier.TECHNICAL, "Recipient private key cannot be loaded"),
    CRYPTOGRAPHIC_FAILURE("ERR-T-005", Tier.TECHNICAL, "JOSE library reported a cryptographic failure"),
    GATEWAY_5XX("ERR-T-006", Tier.TECHNICAL, "HFCX gateway returned a 5xx response after retry exhaustion");

    /** Tier of an error code, identifying which abstract exception class it maps to. */
    public enum Tier {
        PROTOCOL,
        BUSINESS,
        TECHNICAL
    }

    private static final Map<String, ErrorCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

    private final String code;
    private final Tier tier;
    private final String description;

    ErrorCode(String code, Tier tier, String description) {
        this.code = code;
        this.tier = tier;
        this.description = description;
    }

    /** The on-the-wire code, e.g. {@code "ERR-P-001"}. */
    public String code() {
        return code;
    }

    public Tier tier() {
        return tier;
    }

    public String description() {
        return description;
    }

    /** @return the catalog entry for {@code wireCode}, or empty if unknown. */
    public static Optional<ErrorCode> fromWire(String wireCode) {
        if (wireCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(wireCode));
    }
}
