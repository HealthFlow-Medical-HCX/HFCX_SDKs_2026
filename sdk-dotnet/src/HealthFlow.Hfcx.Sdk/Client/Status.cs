// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// Outcome of an HFCX outbound request as observed at the SDK call site.
/// Cross-SDK invariant with Java's <c>Status</c> and Python's
/// <c>Status</c> enum.
/// </summary>
public enum Status
{
    /// <summary>Gateway returned HTTP 202 (Accepted).</summary>
    Accepted,

    /// <summary>Gateway returned a 4xx — the recipient or platform rejected the request.</summary>
    Rejected,

    /// <summary>Reserved: stubbed-mode response from a test harness.</summary>
    Stubbed,
}
