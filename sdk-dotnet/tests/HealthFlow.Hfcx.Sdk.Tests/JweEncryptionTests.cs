// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Jose;
using Xunit;
using HealthFlow.Hfcx.Sdk.Crypto;
using JweEncryption = HealthFlow.Hfcx.Sdk.Crypto.JweEncryption;
using JoseEnc = global::Jose.JweEncryption;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: this test suite mirrors the Java SDK's
/// <c>JweEncryptionTest</c> and the Python SDK's <c>test_crypto.py</c>.
/// Round-trip, downgrade-rejection matrix, malformed input, and a
/// 100&#160;KB / 500&#160;ms performance budget.
/// </summary>
public class JweEncryptionTests
{
    // ── Round-trip happy paths ─────────────────────────────────────

    [Fact]
    public void RoundTrip_AsciiPayload()
    {
        using var rsa = RSA.Create(2048);
        const string plaintext = "{\"resourceType\":\"Bundle\"}";

        var jwe = JweEncryption.EncryptUtf8(plaintext, rsa);
        var decrypted = JweEncryption.DecryptUtf8(jwe, rsa);

        Assert.Equal(plaintext, decrypted);
    }

    [Fact]
    public void RoundTrip_UnicodeMultiBytePayload()
    {
        using var rsa = RSA.Create(2048);
        const string plaintext = "{\"name\":\"محمد علي\",\"city\":\"القاهرة\"}";

        var jwe = JweEncryption.EncryptUtf8(plaintext, rsa);
        var decrypted = JweEncryption.DecryptUtf8(jwe, rsa);

        Assert.Equal(plaintext, decrypted);
    }

    [Fact]
    public void Encrypt_ProducesDistinctCiphertextsAcrossInvocations()
    {
        // GCM uses a fresh nonce on each call; identical plaintext + key
        // must yield distinct ciphertexts.
        using var rsa = RSA.Create(2048);
        const string plaintext = "{\"x\":1}";

        var a = JweEncryption.EncryptUtf8(plaintext, rsa);
        var b = JweEncryption.EncryptUtf8(plaintext, rsa);

        Assert.NotEqual(a, b);
    }

    [Fact]
    public void Encrypt_ProtectedHeaderAdvertisesPinnedAlgorithms()
    {
        using var rsa = RSA.Create(2048);
        var jwe = JweEncryption.EncryptUtf8("{}", rsa);

        var headers = JWT.Headers<System.Collections.Generic.IDictionary<string, object>>(jwe);
        Assert.Equal("RSA-OAEP-256", headers["alg"].ToString());
        Assert.Equal("A256GCM", headers["enc"].ToString());
    }

    [Fact]
    public void RoundTrip_LargeBundle_UnderPerfBudget()
    {
        using var rsa = RSA.Create(2048);
        var plaintext = new string('a', 100 * 1024); // 100 KB

        var sw = Stopwatch.StartNew();
        var jwe = JweEncryption.EncryptUtf8(plaintext, rsa);
        var decrypted = JweEncryption.DecryptUtf8(jwe, rsa);
        sw.Stop();

        Assert.Equal(plaintext, decrypted);
        Assert.True(sw.ElapsedMilliseconds < 500,
            $"100 KB round-trip took {sw.ElapsedMilliseconds} ms (budget: 500 ms)");
    }

    // ── Downgrade-rejection matrix ─────────────────────────────────

    [Theory]
    [InlineData(JweAlgorithm.RSA1_5, JoseEnc.A256GCM, "RSA1_5 must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP, JoseEnc.A256GCM, "RSA-OAEP (SHA-1) must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP_256, JoseEnc.A128GCM, "A128GCM must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP_256, JoseEnc.A192GCM, "A192GCM must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP_256, JoseEnc.A256CBC_HS512, "A256CBC-HS512 must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP_256, JoseEnc.A128CBC_HS256, "A128CBC-HS256 must be rejected")]
    [InlineData(JweAlgorithm.RSA_OAEP_256, JoseEnc.A192CBC_HS384, "A192CBC-HS384 must be rejected")]
    public void Decrypt_DowngradedAlgorithm_RaisesJweAlgorithmRejected(
        JweAlgorithm alg,
        JoseEnc enc,
        string reason)
    {
        using var rsa = RSA.Create(2048);
        var downgraded = JWT.Encode("{}", rsa, alg, enc);

        var ex = Assert.Throws<JweAlgorithmRejectedException>(
            () => JweEncryption.DecryptUtf8(downgraded, rsa));
        Assert.Equal("ERR-P-002", ex.Code);
        Assert.NotNull(reason); // pin the test data
    }

    [Fact]
    public void Decrypt_AlgNoneAttack_RaisesJweAlgorithmRejected()
    {
        // Forge a JWE whose protected header advertises "alg":"none". The
        // header check must fire before any cryptographic operation so the
        // private key never sees this token.
        var header = """{"alg":"none","enc":"A256GCM"}""";
        var headerB64 = Base64Url(System.Text.Encoding.UTF8.GetBytes(header));
        var forged = $"{headerB64}..AAAA.AAAA.AAAA";

        using var rsa = RSA.Create(2048);
        var ex = Assert.Throws<JweAlgorithmRejectedException>(
            () => JweEncryption.DecryptUtf8(forged, rsa));
        Assert.Equal("ERR-P-002", ex.Code);
    }

    // ── Malformed input / null guards ──────────────────────────────

    [Fact]
    public void Encrypt_NullPayload_Throws()
    {
        using var rsa = RSA.Create(2048);
        Assert.Throws<ArgumentNullException>(
            () => JweEncryption.EncryptUtf8(null!, rsa));
    }

    [Fact]
    public void Encrypt_NullKey_Throws()
    {
        Assert.Throws<ArgumentNullException>(
            () => JweEncryption.EncryptUtf8("{}", null!));
    }

    [Fact]
    public void Decrypt_NullToken_Throws()
    {
        using var rsa = RSA.Create(2048);
        Assert.Throws<ArgumentNullException>(
            () => JweEncryption.DecryptUtf8(null!, rsa));
    }

    [Fact]
    public void Decrypt_NullKey_Throws()
    {
        Assert.Throws<ArgumentNullException>(
            () => JweEncryption.DecryptUtf8("xxx", null!));
    }

    [Fact]
    public void Decrypt_GarbageCompactToken_RaisesAlgorithmRejected()
    {
        using var rsa = RSA.Create(2048);
        // "not-a-jwe" is structurally invalid; the header parser fails
        // before any crypto runs. Surfaced as algorithm-rejected so the
        // private key is never consulted.
        var ex = Assert.Throws<JweAlgorithmRejectedException>(
            () => JweEncryption.DecryptUtf8("not-a-jwe", rsa));
        Assert.Equal("ERR-P-002", ex.Code);
    }

    [Fact]
    public void Decrypt_WrongKey_RaisesCryptographicFailure()
    {
        using var producer = RSA.Create(2048);
        using var wrongKey = RSA.Create(2048);
        var jwe = JweEncryption.EncryptUtf8("{}", producer);

        var ex = Assert.Throws<CryptographicFailureException>(
            () => JweEncryption.DecryptUtf8(jwe, wrongKey));
        Assert.Equal("ERR-T-005", ex.Code);
    }

    // ── Pinned-constant assertions ─────────────────────────────────

    [Fact]
    public void JweAlgorithms_ConstantsArePinned()
    {
        Assert.Equal("RSA-OAEP-256", JweAlgorithms.Alg);
        Assert.Equal("A256GCM", JweAlgorithms.Enc);
    }

    [Fact]
    public void RoundTrip_UsingFixtureKeyPair()
    {
        // Smoke-test that the shared cross-SDK fixture key pair round-trips
        // through this SDK. The cross-SDK tests live in their own file.
        using var pub = CrossSdkFixtures.LoadPublicKey();
        using var priv = CrossSdkFixtures.LoadPrivateKey();
        var plaintext = CrossSdkFixtures.LoadPlaintext();

        var jwe = JweEncryption.EncryptUtf8(plaintext, pub);
        var decrypted = JweEncryption.DecryptUtf8(jwe, priv);

        // Plaintext is JSON; compare structurally to absorb whitespace
        // differences if any (there shouldn't be — the encrypt/decrypt
        // path doesn't touch whitespace).
        using var d1 = JsonDocument.Parse(plaintext);
        using var d2 = JsonDocument.Parse(decrypted);
        Assert.Equal(d1.RootElement.GetRawText(), d2.RootElement.GetRawText());
    }

    private static string Base64Url(byte[] data)
    {
        return Convert.ToBase64String(data)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }
}
