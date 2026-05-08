// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Jose;

namespace HealthFlow.Hfcx.Sdk.Crypto;

/// <summary>
/// JWE compact-form encrypt and decrypt for the HFCX protocol. The
/// algorithm pair is hard-pinned to <see cref="JweAlgorithms.Alg"/>
/// (RSA-OAEP-256) + <see cref="JweAlgorithms.Enc"/> (A256GCM).
/// </summary>
/// <remarks>
/// <para>Sister to the Java SDK's
/// <c>eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption</c> and the
/// Python SDK's <c>hfcx_sdk.crypto</c>. Cross-SDK invariant: bytes
/// produced by any SDK's <see cref="EncryptUtf8"/> decrypt cleanly to
/// the same plaintext under any SDK's <see cref="DecryptUtf8"/>.</para>
///
/// <para>The decrypt path inspects the JOSE protected header BEFORE any
/// cryptographic operation runs. A downgrade attempt (RSA1_5,
/// alg=none, weaker GCM variants, CBC mode, etc.) raises
/// <see cref="JweAlgorithmRejectedException"/> (<c>ERR-P-002</c>)
/// without touching the recipient's private key.</para>
/// </remarks>
public static class JweEncryption
{
    /// <summary>
    /// Encrypt <paramref name="payload"/> as UTF-8 with the recipient's
    /// RSA public key. Returns a JWE compact serialization.
    /// </summary>
    /// <exception cref="ArgumentNullException">if either argument is null.</exception>
    /// <exception cref="CryptographicFailureException">
    /// on any underlying JOSE / RSA failure.
    /// </exception>
    public static string EncryptUtf8(string payload, RSA recipientPublicKey)
    {
        ArgumentNullException.ThrowIfNull(payload);
        ArgumentNullException.ThrowIfNull(recipientPublicKey);

        try
        {
            return JWT.Encode(
                payload: payload,
                key: recipientPublicKey,
                alg: JweAlgorithm.RSA_OAEP_256,
                enc: Jose.JweEncryption.A256GCM);
        }
        catch (CryptographicException ex)
        {
            throw new CryptographicFailureException($"JWE encryption failed: {ex.Message}");
        }
        catch (JoseException ex)
        {
            throw new CryptographicFailureException($"JWE encryption failed: {ex.Message}");
        }
    }

    /// <summary>
    /// Decrypt a JWE compact serialization with the recipient's RSA
    /// private key, returning the UTF-8 plaintext.
    /// </summary>
    /// <exception cref="ArgumentNullException">if either argument is null.</exception>
    /// <exception cref="JweAlgorithmRejectedException">
    /// if the protected header advertises any algorithm pair other than
    /// RSA-OAEP-256 + A256GCM. Raised BEFORE any cryptographic
    /// operation runs.
    /// </exception>
    /// <exception cref="CryptographicFailureException">
    /// on any underlying JOSE / RSA failure (post header check).
    /// </exception>
    public static string DecryptUtf8(string jweCompact, RSA recipientPrivateKey)
    {
        ArgumentNullException.ThrowIfNull(jweCompact);
        ArgumentNullException.ThrowIfNull(recipientPrivateKey);

        AssertPinnedAlgorithmsOrThrow(jweCompact);

        try
        {
            return JWT.Decode(
                token: jweCompact,
                key: recipientPrivateKey,
                alg: JweAlgorithm.RSA_OAEP_256,
                enc: Jose.JweEncryption.A256GCM);
        }
        catch (JweAlgorithmRejectedException)
        {
            throw;
        }
        catch (CryptographicException ex)
        {
            throw new CryptographicFailureException($"JWE decryption failed: {ex.Message}");
        }
        catch (IntegrityException ex)
        {
            throw new CryptographicFailureException($"JWE decryption failed: {ex.Message}");
        }
        catch (EncryptionException ex)
        {
            throw new CryptographicFailureException($"JWE decryption failed: {ex.Message}");
        }
        catch (JoseException ex)
        {
            throw new CryptographicFailureException($"JWE decryption failed: {ex.Message}");
        }
    }

    /// <summary>
    /// Inspect the JOSE protected header WITHOUT decrypting and reject
    /// any algorithm pair other than the pinned
    /// <see cref="JweAlgorithms.Alg"/> + <see cref="JweAlgorithms.Enc"/>.
    /// </summary>
    private static void AssertPinnedAlgorithmsOrThrow(string jweCompact)
    {
        IDictionary<string, object>? headers;
        try
        {
            headers = JWT.Headers<IDictionary<string, object>>(jweCompact);
        }
        catch (Exception ex) when (ex is JoseException
            || ex is FormatException
            || ex is ArgumentException
            || ex is System.Text.Json.JsonException)
        {
            throw new JweAlgorithmRejectedException(
                $"JWE protected header is not parseable: {ex.Message}");
        }

        if (headers is null)
        {
            throw new JweAlgorithmRejectedException("JWE protected header is missing");
        }

        var alg = headers.TryGetValue("alg", out var algObj) ? algObj?.ToString() : null;
        var enc = headers.TryGetValue("enc", out var encObj) ? encObj?.ToString() : null;

        if (!string.Equals(alg, JweAlgorithms.Alg, StringComparison.Ordinal))
        {
            throw new JweAlgorithmRejectedException(
                $"JWE alg='{alg}' is not the pinned {JweAlgorithms.Alg}");
        }

        if (!string.Equals(enc, JweAlgorithms.Enc, StringComparison.Ordinal))
        {
            throw new JweAlgorithmRejectedException(
                $"JWE enc='{enc}' is not the pinned {JweAlgorithms.Enc}");
        }
    }

    /// <summary>UTF-8 helper for callers that prefer byte-array surfaces.</summary>
    public static byte[] EncryptUtf8Bytes(string payload, RSA recipientPublicKey)
    {
        var compact = EncryptUtf8(payload, recipientPublicKey);
        return Encoding.ASCII.GetBytes(compact);
    }
}
