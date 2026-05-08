// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Protocol;

/// <summary>
/// Names of the five HFCX protocol headers, pinned cross-SDK in the
/// mixed hyphen+underscore form per Gap 7 backward compatibility.
/// </summary>
/// <remarks>
/// Sister to Java's <c>ProtocolHeaders</c> and Python's
/// <c>hfcx_sdk.protocol</c>. The names are deliberately frozen as
/// <c>const string</c> so cross-SDK byte-level comparisons stay
/// identical. Sprint D2 lands the matching <c>Build(...)</c> helper.
/// </remarks>
public static class ProtocolHeaders
{
    public const string SenderCode = "x-hcx-sender_code";
    public const string RecipientCode = "x-hcx-recipient_code";
    public const string CorrelationId = "x-hcx-correlation_id";
    public const string Timestamp = "x-hcx-timestamp";
    public const string ApiCallId = "x-hcx-api-call-id";
}
