// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Crypto;

/// <summary>
/// Hard-pinned JWE algorithm identifiers used by the HFCX protocol.
/// Cross-SDK invariant: every SDK encrypts with this exact pair and
/// rejects every other pair on decrypt — the rejection happens on the
/// JOSE protected header BEFORE any cryptographic operation.
/// </summary>
public static class JweAlgorithms
{
    /// <summary>Key-wrap algorithm: <c>RSA-OAEP-256</c>.</summary>
    public const string Alg = "RSA-OAEP-256";

    /// <summary>Content-encryption algorithm: <c>A256GCM</c>.</summary>
    public const string Enc = "A256GCM";
}
