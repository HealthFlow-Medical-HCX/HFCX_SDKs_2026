// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Crypto;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Closes the cross-SDK round-trip loop without needing the platform's
/// integration harness: this SDK decrypts JWE compact tokens produced by
/// the Java and Python SDKs against the shared fixture key pair, and
/// asserts the protected header advertises the pinned algorithm pair.
///
/// Sister to the Java SDK's <c>CrossSdkRoundTripTest</c> and the Python
/// SDK's <c>tests/unit/test_cross_sdk_round_trip.py</c>.
/// </summary>
public class CrossSdkRoundTripTests
{
    [Fact]
    public void Decrypts_JavaProducedJwe_BackToFixturePlaintext()
    {
        using var priv = CrossSdkFixtures.LoadPrivateKey();
        var jwe = CrossSdkFixtures.LoadJavaProducedJwe();
        var expected = CrossSdkFixtures.LoadPlaintext();

        var decrypted = JweEncryption.DecryptUtf8(jwe, priv);

        AssertJsonEquivalent(expected, decrypted);
    }

    [Fact]
    public void Decrypts_JavaScriptProducedJwe_BackToFixturePlaintext()
    {
        using var priv = CrossSdkFixtures.LoadPrivateKey();
        var jwe = CrossSdkFixtures.LoadJavaScriptProducedJwe();
        var expected = CrossSdkFixtures.LoadPlaintext();

        var decrypted = JweEncryption.DecryptUtf8(jwe, priv);

        AssertJsonEquivalent(expected, decrypted);
    }

    [Fact]
    public void Decrypts_PythonProducedJwe_BackToFixturePlaintext()
    {
        using var priv = CrossSdkFixtures.LoadPrivateKey();
        var jwe = CrossSdkFixtures.LoadPythonProducedJwe();
        var expected = CrossSdkFixtures.LoadPlaintext();

        var decrypted = JweEncryption.DecryptUtf8(jwe, priv);

        AssertJsonEquivalent(expected, decrypted);
    }

    [Fact]
    public void JavaProducedJwe_HeaderAdvertisesPinnedAlgorithms()
    {
        var jwe = CrossSdkFixtures.LoadJavaProducedJwe();
        var headers = Jose.JWT.Headers<System.Collections.Generic.IDictionary<string, object>>(jwe);

        Assert.Equal("RSA-OAEP-256", headers["alg"].ToString());
        Assert.Equal("A256GCM", headers["enc"].ToString());
    }

    [Fact]
    public void PythonProducedJwe_HeaderAdvertisesPinnedAlgorithms()
    {
        var jwe = CrossSdkFixtures.LoadPythonProducedJwe();
        var headers = Jose.JWT.Headers<System.Collections.Generic.IDictionary<string, object>>(jwe);

        Assert.Equal("RSA-OAEP-256", headers["alg"].ToString());
        Assert.Equal("A256GCM", headers["enc"].ToString());
    }

    [Fact]
    public void DotNetProducedJwe_RoundTripsThroughThisSdk()
    {
        using var pub = CrossSdkFixtures.LoadPublicKey();
        using var priv = CrossSdkFixtures.LoadPrivateKey();
        var plaintext = CrossSdkFixtures.LoadPlaintext();

        var jwe = JweEncryption.EncryptUtf8(plaintext, pub);
        var decrypted = JweEncryption.DecryptUtf8(jwe, priv);

        AssertJsonEquivalent(plaintext, decrypted);
    }

    private static void AssertJsonEquivalent(string expected, string actual)
    {
        using var d1 = JsonDocument.Parse(expected);
        using var d2 = JsonDocument.Parse(actual);
        // Comparing raw text after re-serialisation absorbs whitespace
        // differences in the producer's emitter (Java and Python encode
        // FHIR JSON with different default indentation).
        Assert.Equal(JsonSerializer.Serialize(d1), JsonSerializer.Serialize(d2));
    }
}
