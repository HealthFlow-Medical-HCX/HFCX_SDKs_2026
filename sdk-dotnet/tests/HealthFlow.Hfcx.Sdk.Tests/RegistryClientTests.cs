// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Net;
using System.Net.Http;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Registry;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: this test suite mirrors the Java SDK's
/// <c>RegistryClientTest</c> and the Python SDK's <c>test_registry.py</c>.
/// </summary>
public class RegistryClientTests
{
    private static readonly Uri BaseUrl = new("https://registry.example");

    private static (RegistryClient client, StubHttpMessageHandler stub) BuildClient(
        TimeSpan? preExpiryBuffer = null,
        int? maxEntries = null)
    {
        var stub = new StubHttpMessageHandler();
        var http = new HttpClient(stub);
        var client = new RegistryClient(
            BaseUrl,
            httpClient: http,
            preExpiryBuffer: preExpiryBuffer ?? TimeSpan.FromHours(1),
            maxEntries: maxEntries);
        return (client, stub);
    }

    [Fact]
    public async Task Returns_ParticipantCert_OnHappyPath()
    {
        var (client, stub) = BuildClient();
        var (pem, _, notAfter) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("payerco@hcx-egypt", pem));

        var cert = await client.GetRecipientCertAsync("payerco@hcx-egypt");

        Assert.Equal("payerco@hcx-egypt", cert.ParticipantCode);
        Assert.NotNull(cert.PublicKey);
        Assert.True(Math.Abs((cert.NotAfter - notAfter).TotalSeconds) < 2,
            $"NotAfter {cert.NotAfter:o} != fixture {notAfter:o}");
    }

    [Fact]
    public async Task CacheHit_DoesNotHitNetwork()
    {
        var (client, stub) = BuildClient();
        var (pem, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("payerco@hcx-egypt", pem));

        await client.GetRecipientCertAsync("payerco@hcx-egypt");
        await client.GetRecipientCertAsync("payerco@hcx-egypt");
        await client.GetRecipientCertAsync("payerco@hcx-egypt");

        Assert.Equal(1, stub.CallCount);
    }

    [Fact]
    public async Task CacheMiss_OnDifferentParticipants()
    {
        var (client, stub) = BuildClient();
        var (a, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        var (b, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a@hcx-egypt", a));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("b@hcx-egypt", b));

        await client.GetRecipientCertAsync("a@hcx-egypt");
        await client.GetRecipientCertAsync("b@hcx-egypt");

        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public async Task NotFound_RaisesParticipantNotFound()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueStatus(HttpStatusCode.NotFound, "{}");

        var ex = await Assert.ThrowsAsync<ParticipantNotFoundException>(
            () => client.GetRecipientCertAsync("missing@hcx-egypt"));
        Assert.Equal("ERR-B-001", ex.Code);
    }

    [Fact]
    public async Task EmptyEntityArray_RaisesParticipantNotFound()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK, """{"entity":[]}""");

        await Assert.ThrowsAsync<ParticipantNotFoundException>(
            () => client.GetRecipientCertAsync("missing@hcx-egypt"));
    }

    [Fact]
    public async Task MissingEncryptionCert_RaisesTransport()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK,
            """{"entity":[{"participant_code":"a"}]}""");

        var ex = await Assert.ThrowsAsync<TransportException>(
            () => client.GetRecipientCertAsync("a"));
        Assert.Equal("ERR-T-001", ex.Code);
    }

    [Fact]
    public async Task MalformedPem_RaisesTransport()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a", "not a real pem"));

        await Assert.ThrowsAsync<TransportException>(
            () => client.GetRecipientCertAsync("a"));
    }

    [Fact]
    public async Task MalformedJson_RaisesTransport()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK, "not-json");

        await Assert.ThrowsAsync<TransportException>(
            () => client.GetRecipientCertAsync("a"));
    }

    [Fact]
    public async Task Status503_RaisesRegistryUnavailable()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");

        var ex = await Assert.ThrowsAsync<RegistryUnavailableException>(
            () => client.GetRecipientCertAsync("a"));
        Assert.Equal("ERR-T-003", ex.Code);
    }

    [Fact]
    public async Task NetworkError_RaisesRegistryUnavailable()
    {
        var (client, stub) = BuildClient();
        stub.EnqueueException(new HttpRequestException("connect refused"));

        await Assert.ThrowsAsync<RegistryUnavailableException>(
            () => client.GetRecipientCertAsync("a"));
    }

    [Fact]
    public async Task Invalidate_ForcesRefetch()
    {
        var (client, stub) = BuildClient();
        var (pem1, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        var (pem2, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a", pem1));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a", pem2));

        await client.GetRecipientCertAsync("a");
        client.Invalidate("a");
        await client.GetRecipientCertAsync("a");

        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public async Task InvalidateAll_ForcesRefetch()
    {
        var (client, stub) = BuildClient();
        var (a, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        var (b, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        var (c, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        var (d, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a", a));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("b", b));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("a", c));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("b", d));

        await client.GetRecipientCertAsync("a");
        await client.GetRecipientCertAsync("b");
        client.InvalidateAll();
        await client.GetRecipientCertAsync("a");
        await client.GetRecipientCertAsync("b");

        Assert.Equal(4, stub.CallCount);
    }

    [Fact]
    public async Task ExpiringCert_DoesNotCache()
    {
        var (client, stub) = BuildClient(preExpiryBuffer: TimeSpan.FromHours(1));
        // Cert expires in 30 minutes — already inside the 1h pre-expiry
        // buffer, so the result must not be cached.
        var (pem, _, _) = RegistryTestFixtures.NewSelfSignedCert(
            validFor: TimeSpan.FromMinutes(30));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("expiring", pem));
        var (pem2, _, _) = RegistryTestFixtures.NewSelfSignedCert(
            validFor: TimeSpan.FromMinutes(30));
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("expiring", pem2));

        await client.GetRecipientCertAsync("expiring");
        await client.GetRecipientCertAsync("expiring");

        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public void Construction_FailsFast_OnNullBaseUrl()
    {
        Assert.Throws<ArgumentNullException>(() => new RegistryClient(null!));
    }

    [Fact]
    public async Task ParticipantCode_NullOrEmpty_FailsFast()
    {
        var (client, _) = BuildClient();

        await Assert.ThrowsAsync<ArgumentException>(() => client.GetRecipientCertAsync(""));
        await Assert.ThrowsAsync<ArgumentNullException>(() => client.GetRecipientCertAsync(null!));
    }

    [Fact]
    public async Task Posts_To_SearchEndpoint_WithFilterPayload()
    {
        var (client, stub) = BuildClient();
        var (pem, _, _) = RegistryTestFixtures.NewSelfSignedCert();
        stub.EnqueueJson(HttpStatusCode.OK,
            RegistryTestFixtures.BuildRegistryResponse("payerco@hcx-egypt", pem));

        await client.GetRecipientCertAsync("payerco@hcx-egypt");

        var call = Assert.Single(stub.Calls);
        Assert.Equal(HttpMethod.Post, call.Method);
        Assert.EndsWith("/api/v1/Participant/search", call.RequestUri!.AbsolutePath, StringComparison.Ordinal);
        Assert.NotNull(call.Body);
        Assert.Contains("payerco@hcx-egypt", call.Body, StringComparison.Ordinal);
    }
}
