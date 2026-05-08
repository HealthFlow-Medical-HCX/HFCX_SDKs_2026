// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Globalization;

namespace HealthFlow.Hfcx.Sdk.Protocol;

/// <summary>
/// Names and builder for the five HFCX protocol headers, pinned cross-SDK
/// in the mixed hyphen+underscore form per Gap 7 backward compatibility.
/// </summary>
/// <remarks>
/// Sister to Java's <c>ProtocolHeaders</c> and Python's
/// <c>hfcx_sdk.protocol</c>. The constants are deliberately frozen so
/// cross-SDK byte-level comparisons stay identical, and
/// <see cref="Build"/> emits them in the deterministic order pinned by
/// Integration Guide §24.5.
/// </remarks>
public static class ProtocolHeaders
{
    public const string SenderCode = "x-hcx-sender_code";
    public const string RecipientCode = "x-hcx-recipient_code";
    public const string CorrelationId = "x-hcx-correlation_id";
    public const string Timestamp = "x-hcx-timestamp";
    public const string ApiCallId = "x-hcx-api-call-id";

    /// <summary>
    /// Build the five protocol headers in deterministic order. The
    /// <paramref name="timestamp"/> is rendered in ISO-8601 UTC form
    /// (<c>yyyy-MM-ddTHH:mm:ss.fffffffZ</c>) to match the Java + Python
    /// SDKs' wire format byte-for-byte.
    /// </summary>
    public static IReadOnlyDictionary<string, string> Build(
        string senderCode,
        string recipientCode,
        string correlationId,
        DateTimeOffset timestamp,
        string apiCallId)
    {
        ArgumentException.ThrowIfNullOrEmpty(senderCode);
        ArgumentException.ThrowIfNullOrEmpty(recipientCode);
        ArgumentException.ThrowIfNullOrEmpty(correlationId);
        ArgumentException.ThrowIfNullOrEmpty(apiCallId);

        // Use a Dictionary literal so insertion order — and therefore
        // serialisation order on the wire — is stable.
        return new Dictionary<string, string>(5, StringComparer.Ordinal)
        {
            [SenderCode] = senderCode,
            [RecipientCode] = recipientCode,
            [CorrelationId] = correlationId,
            [Timestamp] = FormatInstant(timestamp),
            [ApiCallId] = apiCallId,
        };
    }

    /// <summary>
    /// ISO-8601 UTC instant in the cross-SDK-pinned form
    /// <c>yyyy-MM-ddTHH:mm:ss.fffZ</c>.
    /// </summary>
    public static string FormatInstant(DateTimeOffset timestamp)
    {
        return timestamp
            .ToUniversalTime()
            .ToString("yyyy-MM-ddTHH:mm:ss.fffZ", CultureInfo.InvariantCulture);
    }
}

