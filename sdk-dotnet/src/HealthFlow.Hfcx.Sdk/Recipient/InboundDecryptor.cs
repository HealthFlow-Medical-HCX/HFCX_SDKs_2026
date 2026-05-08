// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using HealthFlow.Hfcx.Sdk.Crypto;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Composes <see cref="ILocalKeyProvider"/> with
/// <see cref="JweEncryption"/>'s decrypt path. Sister to Java's
/// <c>InboundDecryptor</c> and Python's <c>InboundDecryptor</c>.
/// Cross-SDK parity row 12.
/// </summary>
public sealed class InboundDecryptor
{
    private readonly ILocalKeyProvider _keyProvider;

    /// <summary>Construct over the given key provider.</summary>
    public InboundDecryptor(ILocalKeyProvider keyProvider)
    {
        ArgumentNullException.ThrowIfNull(keyProvider);
        _keyProvider = keyProvider;
    }

    /// <summary>Decrypt a JWE compact serialization back to its UTF-8 plaintext.</summary>
    public string Decrypt(string jweCompact)
    {
        ArgumentNullException.ThrowIfNull(jweCompact);
        using var key = _keyProvider.GetPrivateKey();
        return JweEncryption.DecryptUtf8(jweCompact, key);
    }
}
