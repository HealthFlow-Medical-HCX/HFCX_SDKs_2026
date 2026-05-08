// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Globalization;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Protocol;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Validates the five HFCX protocol headers on an inbound request.
/// </summary>
/// <remarks>
/// Sister to Java's <c>HeaderValidator</c> and Python's
/// <c>HeaderValidator</c>. Cross-SDK invariants:
/// <list type="bullet">
///   <item>All five headers must be present and non-empty.</item>
///   <item><c>x-hcx-recipient_code</c> must equal the local participant code.</item>
///   <item><c>x-hcx-correlation_id</c> and <c>x-hcx-api-call-id</c>
///     must be valid UUIDs.</item>
///   <item><c>x-hcx-timestamp</c> must parse as ISO-8601 and fall
///     within the configured tolerance (default ±5 min).</item>
/// </list>
/// </remarks>
public sealed class HeaderValidator
{
    /// <summary>Default tolerance: ±5 minutes.</summary>
    public static readonly TimeSpan DefaultTimestampTolerance = TimeSpan.FromMinutes(5);

    private readonly string _localParticipantCode;
    private readonly Func<DateTimeOffset> _clock;
    private readonly TimeSpan _tolerance;

    /// <summary>Construct over the local participant code.</summary>
    public HeaderValidator(
        string localParticipantCode,
        Func<DateTimeOffset>? clock = null,
        TimeSpan? timestampTolerance = null)
    {
        ArgumentException.ThrowIfNullOrEmpty(localParticipantCode);
        _localParticipantCode = localParticipantCode;
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _tolerance = timestampTolerance ?? DefaultTimestampTolerance;
    }

    /// <summary>Validate the headers; throws on first violation.</summary>
    public void Validate(IReadOnlyDictionary<string, string> headers)
    {
        ArgumentNullException.ThrowIfNull(headers);

        var sender = RequireHeader(headers, ProtocolHeaders.SenderCode);
        _ = sender; // sender presence is enough at this layer
        var recipient = RequireHeader(headers, ProtocolHeaders.RecipientCode);
        var correlationId = RequireHeader(headers, ProtocolHeaders.CorrelationId);
        var timestamp = RequireHeader(headers, ProtocolHeaders.Timestamp);
        var apiCallId = RequireHeader(headers, ProtocolHeaders.ApiCallId);

        if (!string.Equals(recipient, _localParticipantCode, StringComparison.Ordinal))
        {
            throw new RecipientCodeMismatchException(
                $"x-hcx-recipient_code '{recipient}' does not match local '{_localParticipantCode}'");
        }

        if (!Guid.TryParseExact(correlationId, "D", out _))
        {
            throw new BadUuidException(
                $"x-hcx-correlation_id is not a valid UUID: '{correlationId}'");
        }

        if (!Guid.TryParseExact(apiCallId, "D", out _))
        {
            throw new BadUuidException(
                $"x-hcx-api-call-id is not a valid UUID: '{apiCallId}'");
        }

        DateTimeOffset parsed;
        try
        {
            parsed = DateTimeOffset.Parse(
                timestamp,
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal);
        }
        catch (FormatException)
        {
            throw new BadTimestampException(
                $"x-hcx-timestamp is not valid ISO-8601: '{timestamp}'");
        }

        var delta = parsed - _clock();
        if (delta < -_tolerance || delta > _tolerance)
        {
            throw new TimestampOutOfRangeException(
                $"x-hcx-timestamp '{timestamp}' is outside ±{_tolerance} of the local clock");
        }
    }

    private static string RequireHeader(IReadOnlyDictionary<string, string> headers, string name)
    {
        if (!headers.TryGetValue(name, out var value) || string.IsNullOrEmpty(value))
        {
            throw new MissingHeaderException($"required protocol header '{name}' is missing or empty");
        }

        return value;
    }
}
