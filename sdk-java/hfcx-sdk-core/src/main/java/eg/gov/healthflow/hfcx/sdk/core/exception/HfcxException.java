package eg.gov.healthflow.hfcx.sdk.core.exception;

import java.util.Optional;

/**
 * Root exception type for every error surfaced by the HFCX SDK.
 *
 * <p>Subtypes follow the platform's three-tier error taxonomy
 * (see the platform repo's {@code ErrorCodes.java}):
 * {@link ProtocolException} for {@code ERR-P-xxx}, {@link BusinessException}
 * for {@code ERR-B-xxx}, {@link TechnicalException} for {@code ERR-T-xxx}.
 *
 * <p>The {@link #getCode()} string is the wire-format identifier returned
 * by the platform and is identical across all four language SDKs. Use
 * {@link #of(ErrorCode, String)} or {@link #fromWireCode(String, String)}
 * to construct the most-specific typed subclass for a catalog entry.
 */
public abstract class HfcxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    protected HfcxException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected HfcxException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    protected HfcxException(ErrorCode code, String message) {
        this(code.code(), message);
    }

    protected HfcxException(ErrorCode code, String message, Throwable cause) {
        this(code.code(), message, cause);
    }

    /**
     * Wire-format error code, e.g. {@code "ERR-B-006"}. Identical across
     * the Java, Python, .NET, and JavaScript SDKs.
     */
    public String getCode() {
        return code;
    }

    /**
     * Construct the most-specific typed subclass for {@code code}. The
     * returned exception's runtime type is the dedicated typed subclass
     * (e.g. {@link MissingHeaderException}) rather than the bare tier
     * exception, so callers can {@code catch} by type.
     */
    public static HfcxException of(ErrorCode code, String message) {
        return switch (code) {
            case MISSING_HEADER -> new MissingHeaderException(message);
            case JWE_ALGORITHM_REJECTED -> new JweAlgorithmRejectedException(message);
            case RECIPIENT_CODE_MISMATCH -> new RecipientCodeMismatchException(message);
            case BAD_UUID -> new BadUuidException(message);
            case BAD_TIMESTAMP -> new BadTimestampException(message);
            case TIMESTAMP_OUT_OF_RANGE -> new TimestampOutOfRangeException(message);
            case SENDER_UNKNOWN -> new SenderUnknownException(message);
            case BAD_ENVELOPE -> new BadEnvelopeException(message);
            case SIGNATURE_VERIFICATION_FAILED -> new SignatureVerificationFailedException(message);
            case PARTICIPANT_NOT_FOUND -> new ParticipantNotFoundException(message);
            case NOT_A_BUNDLE -> new NotABundleException(message);
            case BUNDLE_MISSING_TYPE -> new BundleMissingTypeException(message);
            case PATIENT_MISSING_NATIONAL_ID -> new PatientMissingNationalIdException(message);
            case PATIENT_NON_EGYPTIAN -> new PatientNonEgyptianException(message);
            case NATIONAL_ID_INVALID -> new NationalIdInvalidException(message);
            case PHONE_INVALID -> new PhoneInvalidException(message);
            case IBAN_INVALID -> new IbanInvalidException(message);
            case BAD_FHIR_JSON -> new BadFhirJsonException(message);
            case ENVELOPE_MISSING_PAYLOAD -> new EnvelopeMissingPayloadException(message);
            case ENVELOPE_MALFORMED_JSON -> new EnvelopeMalformedJsonException(message);
            case UNKNOWN_BUSINESS -> new UnknownBusinessException(message);
            case TRANSPORT -> new TransportException(message);
            case AUTHENTICATION -> new AuthenticationException(message);
            case REGISTRY_UNAVAILABLE -> new RegistryUnavailableException(message);
            case KEY_UNAVAILABLE -> new KeyUnavailableException(message);
            case CRYPTOGRAPHIC_FAILURE -> new CryptographicFailureException(message);
            case GATEWAY_5XX -> new Gateway5xxException(message);
        };
    }

    public static HfcxException of(ErrorCode code, String message, Throwable cause) {
        // Build the typed instance, then re-throw via the cause-aware
        // constructor on each tier. To avoid duplicating the 27-arm
        // switch, we attach the cause via initCause on the typed result.
        HfcxException typed = of(code, message);
        if (cause != null) {
            typed.initCause(cause);
        }
        return typed;
    }

    /**
     * Look up a wire-format code in the catalog and return the typed
     * subclass for it. If the wire code is not in the SDK's catalog
     * (e.g. the platform added a new code the SDK hasn't synced yet),
     * the result falls back to the bare tier exception based on the
     * {@code ERR-[PBT]-} prefix, preserving the platform's reported
     * code so callers can still log / triage by it.
     */
    public static HfcxException fromWireCode(String wireCode, String message) {
        Optional<ErrorCode> catalog = ErrorCode.fromWire(wireCode);
        if (catalog.isPresent()) {
            return of(catalog.get(), message);
        }
        if (wireCode != null && wireCode.length() > 5) {
            char prefix = wireCode.charAt(4);
            return switch (prefix) {
                case 'P' -> new ProtocolException(wireCode, message);
                case 'B' -> new BusinessException(wireCode, message);
                case 'T' -> new TechnicalException(wireCode, message);
                default -> new UnknownBusinessException(
                        message + " [unknown wire code: " + wireCode + "]");
            };
        }
        return new UnknownBusinessException(
                message + " [unknown wire code: " + wireCode + "]");
    }
}
