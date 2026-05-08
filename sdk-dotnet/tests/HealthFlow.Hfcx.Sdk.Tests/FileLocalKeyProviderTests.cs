// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.IO;
using System.Security.Cryptography;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Recipient;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class FileLocalKeyProviderTests : IDisposable
{
    private readonly string _tmp;

    public FileLocalKeyProviderTests()
    {
        _tmp = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tmp);
    }

    public void Dispose()
    {
        Directory.Delete(_tmp, recursive: true);
    }

    [Fact]
    public void RoundTrip_ReadsKeyAndDecryptsThroughJwe()
    {
        var (path, _) = WritePrivateKey();
        var provider = new FileLocalKeyProvider(path);

        using var loaded = provider.GetPrivateKey();
        const string plaintext = "{\"hi\":1}";
        var jwe = JweEncryption.EncryptUtf8(plaintext, loaded);
        var decrypted = JweEncryption.DecryptUtf8(jwe, loaded);
        Assert.Equal(plaintext, decrypted);
    }

    [Fact]
    public void Reads_OnEveryCall_ToSupportRotation()
    {
        var path = Path.Combine(_tmp, "rotating.pem");
        var (initial, _) = WritePrivateKey(path);
        var provider = new FileLocalKeyProvider(initial);

        using (var first = provider.GetPrivateKey())
        {
            var jwe = JweEncryption.EncryptUtf8("{}", first);
            Assert.Equal("{}", JweEncryption.DecryptUtf8(jwe, first));
        }

        // Rotate the file in place.
        var (_, _) = WritePrivateKey(path);

        using var second = provider.GetPrivateKey();
        var jwe2 = JweEncryption.EncryptUtf8("{}", second);
        Assert.Equal("{}", JweEncryption.DecryptUtf8(jwe2, second));
    }

    [Fact]
    public void MissingFile_RaisesKeyUnavailable()
    {
        var path = Path.Combine(_tmp, "does-not-exist.pem");
        var provider = new FileLocalKeyProvider(path);
        var ex = Assert.Throws<KeyUnavailableException>(() => provider.GetPrivateKey());
        Assert.Equal("ERR-T-004", ex.Code);
    }

    [Fact]
    public void MalformedPem_RaisesKeyUnavailable()
    {
        var path = Path.Combine(_tmp, "bad.pem");
        File.WriteAllText(path, "not a real pem");
        var provider = new FileLocalKeyProvider(path);
        Assert.Throws<KeyUnavailableException>(() => provider.GetPrivateKey());
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void Constructor_RejectsEmptyPath(string? path)
    {
        Assert.ThrowsAny<ArgumentException>(() => new FileLocalKeyProvider(path!));
    }

    private (string path, RSA key) WritePrivateKey(string? destination = null)
    {
        var rsa = RSA.Create(2048);
        var pem = rsa.ExportPkcs8PrivateKeyPem();
        var path = destination ?? Path.Combine(_tmp, $"key-{Guid.NewGuid():N}.pem");
        File.WriteAllText(path, pem);
        return (path, rsa);
    }
}
