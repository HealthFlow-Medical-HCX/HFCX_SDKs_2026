// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Auth;

/// <summary>
/// Validates an inbound <c>Authorization</c> header on the recipient side.
/// The SDK does NOT ship a default trust-everything implementation by
/// design — participants make a deliberate choice when wiring this up.
/// </summary>
/// <remarks>
/// Sister to the Java SDK's <c>BearerTokenValidator</c>
/// <c>@FunctionalInterface</c> and the Python SDK's
/// <c>BearerTokenValidator</c> Protocol. Sprint D5 lands the recipient
/// pipeline that consumes this; D3 ships the contract.
/// </remarks>
public interface IBearerTokenValidator
{
    /// <summary>
    /// Validate the raw <c>Authorization</c> header value (typically
    /// <c>"Bearer eyJ..."</c>).
    /// </summary>
    /// <exception cref="HealthFlow.Hfcx.Sdk.Exceptions.AuthenticationException">
    /// when the header is missing, malformed, or the token is rejected.
    /// </exception>
    void Validate(string? authorizationHeader);
}
