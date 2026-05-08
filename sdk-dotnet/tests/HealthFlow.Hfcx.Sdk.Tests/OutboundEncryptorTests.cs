// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Client;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Registry;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class OutboundEncryptorTests
{
    [Fact]
    public async Task Encrypt_ProducesJweCompact_DecryptableWithMatchingPrivateKey()
    {
        using var rsa = RSA.Create(2048);
        var resolver = new StaticResolver("payerco@hcx-egypt", rsa, DateTimeOffset.UtcNow.AddHours(2));
        var encryptor = new OutboundEncryptor(resolver);

        var jwe = await encryptor.EncryptAsync("{\"hello\":\"world\"}", "payerco@hcx-egypt");

        var decrypted = JweEncryption.DecryptUtf8(jwe, rsa);
        Assert.Equal("{\"hello\":\"world\"}", decrypted);
    }

    [Fact]
    public async Task Encrypt_PropagatesResolverFailures()
    {
        var encryptor = new OutboundEncryptor(new ThrowingResolver());

        await Assert.ThrowsAsync<InvalidOperationException>(
            () => encryptor.EncryptAsync("{}", "missing"));
    }

    [Fact]
    public void Constructor_RejectsNullResolver()
    {
        Assert.Throws<ArgumentNullException>(() => new OutboundEncryptor(null!));
    }

    [Fact]
    public async Task Encrypt_RejectsNullPayload()
    {
        using var rsa = RSA.Create(2048);
        var encryptor = new OutboundEncryptor(
            new StaticResolver("a", rsa, DateTimeOffset.UtcNow.AddHours(1)));

        await Assert.ThrowsAsync<ArgumentNullException>(() => encryptor.EncryptAsync(null!, "a"));
    }

    [Fact]
    public async Task Encrypt_RejectsEmptyRecipientCode()
    {
        using var rsa = RSA.Create(2048);
        var encryptor = new OutboundEncryptor(
            new StaticResolver("a", rsa, DateTimeOffset.UtcNow.AddHours(1)));

        await Assert.ThrowsAsync<ArgumentException>(() => encryptor.EncryptAsync("{}", ""));
    }

    private sealed class StaticResolver : IRecipientCertResolver
    {
        private readonly ParticipantCert _cert;

        public StaticResolver(string code, RSA key, DateTimeOffset notAfter)
        {
            _cert = new ParticipantCert(code, key, notAfter);
        }

        public Task<ParticipantCert> GetRecipientCertAsync(
            string participantCode,
            CancellationToken cancellationToken = default)
            => Task.FromResult(_cert);
    }

    private sealed class ThrowingResolver : IRecipientCertResolver
    {
        public Task<ParticipantCert> GetRecipientCertAsync(
            string participantCode,
            CancellationToken cancellationToken = default)
            => throw new InvalidOperationException("resolver failed");
    }
}
