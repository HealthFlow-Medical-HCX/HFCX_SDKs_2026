// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Security.Cryptography;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Abstraction over the recipient's RSA private key source. Sister to
/// the Java SDK's <c>LocalKeyProvider</c>
/// <c>@FunctionalInterface</c> and the Python SDK's
/// <c>LocalKeyProvider</c> Protocol.
/// </summary>
/// <remarks>
/// Two reference implementations ship with the SDK:
/// <see cref="FileLocalKeyProvider"/> for PKCS#8 PEM on disk and
/// <see cref="VaultLocalKeyProvider"/> for HashiCorp Vault KV v2.
/// Custom resolvers (HSM, Azure Key Vault, AWS KMS, …) implement this
/// interface.
/// </remarks>
public interface ILocalKeyProvider
{
    /// <summary>Return the recipient's RSA private key.</summary>
    /// <exception cref="HealthFlow.Hfcx.Sdk.Exceptions.KeyUnavailableException">
    /// if the key cannot be loaded (file missing, malformed PEM, Vault
    /// unreachable, etc.).
    /// </exception>
    RSA GetPrivateKey();
}
