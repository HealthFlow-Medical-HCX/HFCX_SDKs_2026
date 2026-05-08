// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Exceptions;

/// <summary>
/// Tier of an HFCX error code, identifying which abstract exception class it
/// maps to. Cross-SDK invariant: matches <c>ErrorCode.Tier</c> on Java,
/// <c>Tier</c> on Python, and the <c>"P" / "B" / "T"</c> wire-code prefixes.
/// </summary>
public enum Tier
{
    /// <summary>Protocol / wire-format violation (<c>ERR-P-*</c>).</summary>
    Protocol,

    /// <summary>Business / FHIR / Egyptian-profile violation (<c>ERR-B-*</c>).</summary>
    Business,

    /// <summary>Transport / cryptographic / IO failure (<c>ERR-T-*</c>).</summary>
    Technical,
}
