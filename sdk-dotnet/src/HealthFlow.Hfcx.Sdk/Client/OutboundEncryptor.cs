// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Registry;

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// Composes <see cref="IRecipientCertResolver"/> with <see cref="JweEncryption"/>:
/// given a recipient participant code and a payload (typically a FHIR
/// Bundle as JSON), looks up the recipient's encryption cert and returns
/// a JWE compact serialization ready to drop into the HFCX request envelope.
/// </summary>
/// <remarks>
/// Sister to Java's <c>OutboundEncryptor</c> and Python's
/// <c>OutboundEncryptor</c> / <c>AsyncOutboundEncryptor</c>. Cross-SDK
/// parity row 11.
/// </remarks>
public sealed class OutboundEncryptor
{
    private readonly IRecipientCertResolver _resolver;

    /// <summary>Construct an encryptor over the given resolver.</summary>
    public OutboundEncryptor(IRecipientCertResolver resolver)
    {
        ArgumentNullException.ThrowIfNull(resolver);
        _resolver = resolver;
    }

    /// <summary>
    /// Encrypt <paramref name="payload"/> for <paramref name="recipientCode"/>.
    /// </summary>
    public async Task<string> EncryptAsync(
        string payload,
        string recipientCode,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(payload);
        ArgumentException.ThrowIfNullOrEmpty(recipientCode);

        var cert = await _resolver
            .GetRecipientCertAsync(recipientCode, cancellationToken)
            .ConfigureAwait(false);
        return JweEncryption.EncryptUtf8(payload, cert.PublicKey);
    }
}
