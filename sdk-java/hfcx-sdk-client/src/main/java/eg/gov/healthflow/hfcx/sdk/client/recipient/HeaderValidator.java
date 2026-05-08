package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.core.exception.BadTimestampException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BadUuidException;
import eg.gov.healthflow.hfcx.sdk.core.exception.MissingHeaderException;
import eg.gov.healthflow.hfcx.sdk.core.exception.RecipientCodeMismatchException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TimestampOutOfRangeException;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates the five protocol headers attached by an HFCX sender.
 *
 * <p>Checks performed:
 *
 * <ul>
 *   <li>All five header names are present and non-empty.</li>
 *   <li>{@code x-hcx-recipient_code} matches the local participant code.</li>
 *   <li>{@code x-hcx-correlation_id} is a UUID (any version).</li>
 *   <li>{@code x-hcx-api-call-id} is a UUID (any version).</li>
 *   <li>{@code x-hcx-timestamp} parses as ISO-8601 and is within
 *       {@link #DEFAULT_TIMESTAMP_TOLERANCE} of the current time.</li>
 * </ul>
 *
 * <p>Each violation surfaces as a typed
 * {@link eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException}
 * subclass with a documented {@code ERR-P-*} code.
 */
public final class HeaderValidator {

    public static final Duration DEFAULT_TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);

    public static final String CODE_MISSING_HEADER = MissingHeaderException.CODE;
    public static final String CODE_RECIPIENT_MISMATCH = RecipientCodeMismatchException.CODE;
    public static final String CODE_BAD_UUID = BadUuidException.CODE;
    public static final String CODE_BAD_TIMESTAMP = BadTimestampException.CODE;
    public static final String CODE_TIMESTAMP_OUT_OF_RANGE = TimestampOutOfRangeException.CODE;

    private static final Pattern UUID_ANY_VERSION = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final String localParticipantCode;
    private final Clock clock;
    private final Duration timestampTolerance;

    public HeaderValidator(String localParticipantCode) {
        this(localParticipantCode, Clock.systemUTC(), DEFAULT_TIMESTAMP_TOLERANCE);
    }

    public HeaderValidator(String localParticipantCode, Clock clock, Duration timestampTolerance) {
        this.localParticipantCode = Objects.requireNonNull(
                localParticipantCode, "localParticipantCode");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timestampTolerance = Objects.requireNonNull(timestampTolerance, "timestampTolerance");
    }

    public void validate(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers");

        String sender = require(headers, ProtocolHeaders.SENDER_CODE);
        String recipient = require(headers, ProtocolHeaders.RECIPIENT_CODE);
        String correlationId = require(headers, ProtocolHeaders.CORRELATION_ID);
        String timestamp = require(headers, ProtocolHeaders.TIMESTAMP);
        String apiCallId = require(headers, ProtocolHeaders.API_CALL_ID);

        if (!localParticipantCode.equals(recipient)) {
            throw new RecipientCodeMismatchException(
                    "x-hcx-recipient_code '" + recipient + "' does not match this participant '"
                            + localParticipantCode + "'");
        }
        if (!UUID_ANY_VERSION.matcher(correlationId).matches()) {
            throw new BadUuidException(
                    "x-hcx-correlation_id is not a valid UUID: '" + correlationId + "'");
        }
        if (!UUID_ANY_VERSION.matcher(apiCallId).matches()) {
            throw new BadUuidException(
                    "x-hcx-api-call-id is not a valid UUID: '" + apiCallId + "'");
        }

        Instant parsed;
        try {
            parsed = Instant.parse(timestamp);
        } catch (DateTimeException e) {
            throw new BadTimestampException(
                    "x-hcx-timestamp is not a valid ISO-8601 instant: '" + timestamp + "'", e);
        }
        Duration delta = Duration.between(parsed, clock.instant()).abs();
        if (delta.compareTo(timestampTolerance) > 0) {
            throw new TimestampOutOfRangeException(
                    "x-hcx-timestamp '" + timestamp
                            + "' is " + delta.toSeconds() + "s from current time; tolerance is "
                            + timestampTolerance.toSeconds() + "s");
        }

        // Sender code presence is required; we don't currently verify it
        // against the registry (that's a J6 concern: full sender-known
        // policy enforcement). Keep the binding silent so unused-warnings
        // stay quiet.
        Objects.requireNonNull(sender);
    }

    private static String require(Map<String, String> headers, String key) {
        String value = headers.get(key);
        if (value == null || value.isEmpty()) {
            throw new MissingHeaderException(
                    "required protocol header '" + key + "' is missing or empty");
        }
        return value;
    }
}
