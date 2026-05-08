// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: this test suite mirrors the Java SDK's
/// <c>KeycloakTokenClientTest</c> and the Python SDK's <c>test_keycloak.py</c>.
/// </summary>
public class KeycloakTokenClientTests
{
    private static readonly Uri TokenUrl =
        new("https://idp.example/realms/hcx/protocol/openid-connect/token");

    private static (KeycloakTokenClient client, StubHttpMessageHandler stub, FakeClock clock)
        BuildClient(TimeSpan? lead = null)
    {
        var stub = new StubHttpMessageHandler();
        var http = new HttpClient(stub) { BaseAddress = null };
        var clock = new FakeClock(new DateTimeOffset(2026, 5, 8, 0, 0, 0, TimeSpan.Zero));
        var client = new KeycloakTokenClient(
            tokenUrl: TokenUrl,
            clientId: "myhospital",
            clientSecret: "shh-secret",
            httpClient: http,
            refreshLeadTime: lead ?? TimeSpan.FromSeconds(60),
            clock: () => clock.Now,
            backoff: _ => TimeSpan.Zero);
        return (client, stub, clock);
    }

    [Fact]
    public async Task Returns_AccessToken_OnHappyPath()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK,
            """{"access_token":"abc","token_type":"Bearer","expires_in":300}""");

        var token = await client.GetTokenAsync();

        Assert.Equal("abc", token);
        Assert.Equal(1, stub.CallCount);
    }

    [Fact]
    public async Task Caches_FreshToken_AcrossCalls()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK,
            """{"access_token":"cached","expires_in":300}""");

        await client.GetTokenAsync();
        await client.GetTokenAsync();

        Assert.Equal(1, stub.CallCount);
    }

    [Fact]
    public async Task Refreshes_BeforeExpiry()
    {
        var (client, stub, clock) = BuildClient(lead: TimeSpan.FromSeconds(60));
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"first","expires_in":300}""");
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"second","expires_in":300}""");

        var t1 = await client.GetTokenAsync();
        clock.Advance(TimeSpan.FromSeconds(241)); // 60s before exp = 240s in
        var t2 = await client.GetTokenAsync();

        Assert.Equal("first", t1);
        Assert.Equal("second", t2);
        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public async Task Status401_RaisesAuthenticationImmediately()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueStatus(HttpStatusCode.Unauthorized, "{\"error\":\"invalid_client\"}");

        var ex = await Assert.ThrowsAsync<AuthenticationException>(
            () => client.GetTokenAsync());
        Assert.Equal("ERR-T-002", ex.Code);
        Assert.Equal(1, stub.CallCount); // no retry on 401
    }

    [Fact]
    public async Task Status503_RetriesUntilSuccess()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        stub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"survived","expires_in":300}""");

        var token = await client.GetTokenAsync();
        Assert.Equal("survived", token);
        Assert.Equal(3, stub.CallCount);
    }

    [Fact]
    public async Task Status503_ExhaustsRetries_RaisesTransport()
    {
        var (client, stub, _) = BuildClient();
        for (var i = 0; i < 4; i++)
        {
            stub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        }

        var ex = await Assert.ThrowsAsync<TransportException>(() => client.GetTokenAsync());
        Assert.Equal("ERR-T-001", ex.Code);
        Assert.Equal(4, stub.CallCount);
    }

    [Fact]
    public async Task NetworkError_RetriesUntilSuccess()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueException(new HttpRequestException("connect refused"));
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"recovered","expires_in":300}""");

        var token = await client.GetTokenAsync();
        Assert.Equal("recovered", token);
        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public async Task Invalidate_ForcesRefetch()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"first","expires_in":300}""");
        stub.EnqueueJson(HttpStatusCode.OK, """{"access_token":"second","expires_in":300}""");

        var t1 = await client.GetTokenAsync();
        client.Invalidate();
        var t2 = await client.GetTokenAsync();

        Assert.Equal("first", t1);
        Assert.Equal("second", t2);
        Assert.Equal(2, stub.CallCount);
    }

    [Fact]
    public async Task MalformedResponse_RaisesTransportImmediately()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK, "not-json");

        await Assert.ThrowsAsync<TransportException>(() => client.GetTokenAsync());
        Assert.Equal(1, stub.CallCount);
    }

    [Fact]
    public async Task MissingAccessToken_RaisesTransport()
    {
        var (client, stub, _) = BuildClient();
        stub.EnqueueJson(HttpStatusCode.OK, """{"token_type":"Bearer","expires_in":300}""");

        await Assert.ThrowsAsync<TransportException>(() => client.GetTokenAsync());
    }

    [Fact]
    public async Task ConcurrentWaiters_CollapseToSingleFetch()
    {
        var (client, stub, _) = BuildClient();
        // The stub returns a slow response so 16 concurrent callers all
        // queue behind the SemaphoreSlim. After the first finishes the
        // rest must hit the cache, not the wire.
        stub.Enqueue(async _ =>
        {
            await Task.Delay(50);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    """{"access_token":"shared","expires_in":300}""",
                    System.Text.Encoding.UTF8,
                    "application/json"),
            };
        });

        var tasks = new Task<string>[16];
        for (var i = 0; i < 16; i++)
        {
            tasks[i] = client.GetTokenAsync();
        }

        var results = await Task.WhenAll(tasks);
        Assert.All(results, t => Assert.Equal("shared", t));
        Assert.Equal(1, stub.CallCount);
    }

    [Fact]
    public void Construction_FailsFast_OnMissingClientId()
    {
        using var http = new HttpClient(new StubHttpMessageHandler());
        Assert.Throws<ArgumentException>(() =>
            new KeycloakTokenClient(TokenUrl, clientId: "", clientSecret: "x", httpClient: http));
    }

    [Fact]
    public void Construction_FailsFast_OnMissingClientSecret()
    {
        using var http = new HttpClient(new StubHttpMessageHandler());
        Assert.Throws<ArgumentException>(() =>
            new KeycloakTokenClient(TokenUrl, clientId: "x", clientSecret: "", httpClient: http));
    }

    [Fact]
    public void Construction_FailsFast_OnMissingTokenUrl()
    {
        using var http = new HttpClient(new StubHttpMessageHandler());
        Assert.Throws<ArgumentNullException>(() =>
            new KeycloakTokenClient(null!, clientId: "x", clientSecret: "y", httpClient: http));
    }

    [Fact]
    public async Task Cancellation_PropagatesAndAbortsBeforeRetry()
    {
        var (client, stub, _) = BuildClient();
        for (var i = 0; i < 4; i++)
        {
            stub.EnqueueStatus(HttpStatusCode.ServiceUnavailable);
        }

        using var cts = new CancellationTokenSource();
        cts.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(
            () => client.GetTokenAsync(cts.Token));
    }

    [Fact]
    public void DoesNotPersistTokensToDisk()
    {
        // Structural assertion: the client must not hold any FileStream or
        // similar IO resource. Sister to the Java test's reflective scan.
        var fields = typeof(KeycloakTokenClient).GetFields(
            System.Reflection.BindingFlags.Instance
            | System.Reflection.BindingFlags.NonPublic
            | System.Reflection.BindingFlags.Public);
        foreach (var f in fields)
        {
            Assert.False(typeof(System.IO.FileStream).IsAssignableFrom(f.FieldType),
                $"{f.Name} looks like file IO");
            Assert.False(typeof(System.IO.Stream).IsAssignableFrom(f.FieldType),
                $"{f.Name} looks like stream IO");
        }
    }

    private sealed class FakeClock
    {
        public FakeClock(DateTimeOffset now)
        {
            Now = now;
        }

        public DateTimeOffset Now { get; private set; }

        public void Advance(TimeSpan delta) => Now += delta;
    }
}
