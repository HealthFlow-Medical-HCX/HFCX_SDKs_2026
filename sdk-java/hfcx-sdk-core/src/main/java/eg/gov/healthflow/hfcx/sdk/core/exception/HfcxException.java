package eg.gov.healthflow.hfcx.sdk.core.exception;

/**
 * Root exception type for every error surfaced by the HFCX SDK.
 *
 * <p>Subtypes follow the platform's three-tier error taxonomy
 * (see {@code ErrorCodes.java} in the {@code hfcx-platform} repo):
 * {@link ProtocolException} for {@code ERR-P-xxx}, {@code BusinessException}
 * for {@code ERR-B-xxx}, {@code TechnicalException} for {@code ERR-T-xxx}.
 *
 * <p>The {@link #getCode()} string is the wire-format identifier returned
 * by the platform and is identical across all four language SDKs.
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

    /**
     * Wire-format error code, e.g. {@code "ERR-B-006"}. Identical across
     * the Java, Python, .NET, and JavaScript SDKs.
     */
    public String getCode() {
        return code;
    }
}
