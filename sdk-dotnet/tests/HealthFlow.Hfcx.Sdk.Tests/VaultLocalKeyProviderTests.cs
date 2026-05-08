// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Recipient;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class VaultLocalKeyProviderTests
{
    private static readonly Uri VaultUrl = new("https://vault.example");
    private const string SecretPath = "hfcx/private-key";

    [Fact]
    public void SuccessfulFetch_ReturnsParsedKey()
    {
        var pem = NewPkcs8Pem();
        var stub = new StubHttpMessageHandler();
        stub.EnqueueJson(HttpStatusCode.OK,
            VaultBody("value", pem));

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token", httpClient: http);

        using var key = provider.GetPrivateKey();
        Assert.NotNull(key);

        var call = Assert.Single(stub.Calls);
        Assert.Equal(HttpMethod.Get, call.Method);
        Assert.Equal($"/v1/secret/data/{SecretPath}", call.RequestUri!.AbsolutePath);
        Assert.Equal(new[] { "hvs.test-token" }, call.Headers.GetValues("X-Vault-Token"));
    }

    [Fact]
    public void NamespaceHeader_IncludedWhenConfigured()
    {
        var pem = NewPkcs8Pem();
        var stub = new StubHttpMessageHandler();
        stub.EnqueueJson(HttpStatusCode.OK,
            VaultBody("value", pem));

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token",
            vaultNamespace: "egypt-tenant",
            httpClient: http);
        using var key = provider.GetPrivateKey();

        var call = Assert.Single(stub.Calls);
        Assert.Equal(new[] { "egypt-tenant" }, call.Headers.GetValues("X-Vault-Namespace"));
    }

    [Theory]
    [InlineData(HttpStatusCode.Forbidden)]
    [InlineData(HttpStatusCode.NotFound)]
    public void Status403Or404_RaisesKeyUnavailable(HttpStatusCode code)
    {
        var stub = new StubHttpMessageHandler();
        stub.EnqueueStatus(code, "{}");

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token", httpClient: http);

        var ex = Assert.Throws<KeyUnavailableException>(() => provider.GetPrivateKey());
        Assert.Equal("ERR-T-004", ex.Code);
    }

    [Fact]
    public void MissingValueField_RaisesKeyUnavailable()
    {
        var stub = new StubHttpMessageHandler();
        stub.EnqueueJson(HttpStatusCode.OK, """{"data":{"data":{"other":"x"}}}""");

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token", httpClient: http);

        var ex = Assert.Throws<KeyUnavailableException>(() => provider.GetPrivateKey());
        Assert.Contains("value", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void CustomSecretField_IsHonoured()
    {
        var pem = NewPkcs8Pem();
        var stub = new StubHttpMessageHandler();
        stub.EnqueueJson(HttpStatusCode.OK,
            VaultBody("private_key", pem));

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token",
            secretField: "private_key",
            httpClient: http);
        using var key = provider.GetPrivateKey();
        Assert.NotNull(key);
    }

    [Fact]
    public void MalformedPem_RaisesKeyUnavailable()
    {
        var stub = new StubHttpMessageHandler();
        stub.EnqueueJson(HttpStatusCode.OK,
            """{"data":{"data":{"value":"not a real pem"}}}""");

        using var http = new HttpClient(stub);
        using var provider = new VaultLocalKeyProvider(
            VaultUrl, SecretPath, "hvs.test-token", httpClient: http);

        Assert.Throws<KeyUnavailableException>(() => provider.GetPrivateKey());
    }

    [Theory]
    [InlineData("", "p", "t")]
    [InlineData("u", "", "t")]
    [InlineData("u", "p", "")]
    public void Constructor_ValidatesRequiredArgs(string baseUrl, string path, string token)
    {
        Uri? url = string.IsNullOrEmpty(baseUrl) ? null : new Uri("https://x.example");
        // Empty baseUrl uses null Uri argument so we hit ArgumentNullException;
        // empty path/token hit ArgumentException.
        if (url is null)
        {
            Assert.Throws<ArgumentNullException>(() =>
                new VaultLocalKeyProvider(null!, path, token));
        }
        else
        {
            Assert.ThrowsAny<ArgumentException>(() =>
                new VaultLocalKeyProvider(url, path, token));
        }
    }

    private static string NewPkcs8Pem()
    {
        using var rsa = RSA.Create(2048);
        return rsa.ExportPkcs8PrivateKeyPem();
    }

    private static string Escape(string pem) =>
        pem.Replace("\r", "", StringComparison.Ordinal)
           .Replace("\n", "\\n", StringComparison.Ordinal);

    private static string VaultBody(string field, string pem) =>
        "{\"data\":{\"data\":{\"" + field + "\":\"" + Escape(pem) + "\"}}}";
}
