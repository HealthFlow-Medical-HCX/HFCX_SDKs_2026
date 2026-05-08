// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Client;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Protocol;
using HealthFlow.Hfcx.Sdk.Registry;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: this suite mirrors the Java SDK's
/// <c>HfcxClientTest</c> and the Python SDK's <c>test_client.py</c>.
/// Covers all five operations, retry behaviour, 401 handling, typed
/// 4xx mapping, envelope shape, every protocol header on the wire, and
/// correlation-ID propagation.
/// </summary>
public class HfcxClientTests : IDisposable
{
    private static readonly Uri GatewayUrl = new("https://gateway.example");

    private readonly RSA _rsa;
    private readonly StubHttpMessageHandler _gatewayStub;
    private readonly StubHttpMessageHandler _keycloakStub;
    private readonly HttpClient _gatewayHttp;
    private readonly HttpClient _keycloakHttp;
    private readonly KeycloakTokenClient _keycloak;
    private readonly OutboundEncryptor _encryptor;
    private readonly HfcxClient _client;
    private int _correlationCounter;
    private int _apiCallCounter;

    public HfcxClientTests()
    {
        _rsa = RSA.Create(2048);
        _gatewayStub = new StubHttpMessageHandler();
        _gatewayHttp = new HttpClient(_gatewayStub);
        _keycloakStub = new StubHttpMessageHandler();
        _keycloakHttp = new HttpClient(_keycloakStub);
        _keycloak = new KeycloakTokenClient(
            tokenUrl: new Uri("https://idp.example/token"),
            clientId: "myhospital",
            clientSecret: "shh",
            httpClient: _keycloakHttp,
            backoff: _ => TimeSpan.Zero);
        _keycloakStub.EnqueueJson(HttpStatusCode.OK,
            """{"access_token":"the-bearer","expires_in":300}""");
        _encryptor = new OutboundEncryptor(
            new StaticResolver("payerco@hcx-egypt", _rsa, DateTimeOffset.UtcNow.AddHours(2)));

        _client = new HfcxClient(
            gatewayUrl: GatewayUrl,
            participantCode: "myhospital@hcx-egypt",
            keycloak: _keycloak,
            encryptor: _encryptor,
            httpClient: _gatewayHttp,
            correlationIdGenerator: () => $"cid-{++_correlationCounter:D4}",
            apiCallIdGenerator: () => $"api-{++_apiCallCounter:D4}",
            delay: (_, _) => Task.CompletedTask); // no real sleep in tests
    }

    public void Dispose()
    {
        _client.Dispose();
        _keycloak.Dispose();
        _gatewayHttp.Dispose();
        _keycloakHttp.Dispose();
        _rsa.Dispose();
    }

    // ── Five-operation matrix ──────────────────────────────────────

    public static IEnumerable<object[]> EveryOperation =>
    [
        ["/v1/coverageeligibility/check", Operation.CheckEligibility],
        ["/v1/preauth/submit", Operation.SubmitPreauth],
        ["/v1/claim/submit", Operation.SubmitClaim],
        ["/v1/communication/on_request", Operation.SendCommunication],
        ["/v1/paymentnotice/notify", Operation.NotifyPayment],
    ];

    [Theory]
    [MemberData(nameof(EveryOperation))]
    public async Task EveryOperation_PostsToConfiguredEndpoint(string path, Operation op)
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        var resp = await Send(op, "{\"resourceType\":\"Bundle\"}");

        Assert.Equal(Status.Accepted, resp.Status);
        var call = Assert.Single(_gatewayStub.Calls);
        Assert.Equal(HttpMethod.Post, call.Method);
        Assert.EndsWith(path, call.RequestUri!.AbsolutePath, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Status202_ReturnsAcceptedWithCallerCorrelationId()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");

        var resp = await _client.SubmitClaimAsync(new SubmitClaimRequest(
            "payerco@hcx-egypt",
            "{\"resourceType\":\"Bundle\"}",
            "caller-correlation"));

        Assert.Equal(Status.Accepted, resp.Status);
        Assert.Equal("caller-correlation", resp.CorrelationId);
    }

    [Fact]
    public async Task Status202_GeneratesCorrelationIdWhenCallerLeavesItNull()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");

        var resp = await Send(Operation.SubmitClaim, "{}");

        Assert.StartsWith("cid-", resp.CorrelationId, StringComparison.Ordinal);
    }

    // ── Wire-shape assertions ──────────────────────────────────────

    [Fact]
    public async Task Envelope_IsJsonWithPayloadField_ContainingJweCompactToken()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        await Send(Operation.SubmitClaim, "{\"hi\":1}");

        var call = Assert.Single(_gatewayStub.Calls);
        using var doc = JsonDocument.Parse(call.Body!);
        var jwe = doc.RootElement.GetProperty("payload").GetString()!;
        Assert.Equal(5, jwe.Split('.').Length); // JWE compact = 5 segments
    }

    [Fact]
    public async Task Wire_AttachesAllFiveProtocolHeadersAndBearerAndUserAgent()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        await Send(Operation.SubmitClaim, "{}");

        var call = Assert.Single(_gatewayStub.Calls);
        Assert.True(call.Headers.Contains(ProtocolHeaders.SenderCode));
        Assert.True(call.Headers.Contains(ProtocolHeaders.RecipientCode));
        Assert.True(call.Headers.Contains(ProtocolHeaders.CorrelationId));
        Assert.True(call.Headers.Contains(ProtocolHeaders.Timestamp));
        Assert.True(call.Headers.Contains(ProtocolHeaders.ApiCallId));

        Assert.Equal("Bearer", call.Headers.Authorization?.Scheme);
        Assert.Equal("the-bearer", call.Headers.Authorization?.Parameter);

        var ua = string.Join(" ", call.Headers.UserAgent.Select(u => u.ToString()));
        Assert.StartsWith("hfcx-sdk-dotnet/", ua, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Wire_RecipientHeaderMatchesRequest()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        await Send(Operation.SubmitClaim, "{}");

        var call = Assert.Single(_gatewayStub.Calls);
        Assert.Equal(
            new[] { "payerco@hcx-egypt" },
            call.Headers.GetValues(ProtocolHeaders.RecipientCode).ToArray());
    }

    [Fact]
    public async Task Wire_DecryptsBackToOriginalPayload()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        const string payload = "{\"resourceType\":\"Bundle\",\"id\":\"abc\"}";
        await Send(Operation.SubmitClaim, payload);

        var call = Assert.Single(_gatewayStub.Calls);
        using var doc = JsonDocument.Parse(call.Body!);
        var jwe = doc.RootElement.GetProperty("payload").GetString()!;
        var decrypted = JweEncryption.DecryptUtf8(jwe, _rsa);
        Assert.Equal(payload, decrypted);
    }

    // ── Retry / 401 / 4xx / typed mapping ──────────────────────────

    [Fact]
    public async Task Status503_RetriesUntilSuccess()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        _gatewayStub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");

        var resp = await Send(Operation.SubmitClaim, "{}");
        Assert.Equal(Status.Accepted, resp.Status);
        Assert.Equal(3, _gatewayStub.CallCount);
    }

    [Fact]
    public async Task Status503_ExhaustsRetries_RaisesGateway5xx()
    {
        for (var i = 0; i < 4; i++)
        {
            _gatewayStub.EnqueueStatus(HttpStatusCode.ServiceUnavailable, "boom");
        }

        var ex = await Assert.ThrowsAsync<Gateway5xxException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Equal("ERR-T-006", ex.Code);
        Assert.Equal(4, _gatewayStub.CallCount);
    }

    [Fact]
    public async Task Status401_InvalidatesBearerAndRaisesAuthenticationException()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.Unauthorized, "{}");

        var ex = await Assert.ThrowsAsync<AuthenticationException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Equal("ERR-T-002", ex.Code);

        // Subsequent call refetches the bearer token.
        _keycloakStub.EnqueueJson(HttpStatusCode.OK,
            """{"access_token":"new-bearer","expires_in":300}""");
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");
        await Send(Operation.SubmitClaim, "{}");
        Assert.Equal(2, _keycloakStub.CallCount);
    }

    [Fact]
    public async Task Status400_WithErrorCodeBody_RaisesTypedSubclass()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.BadRequest,
            """{"error":{"code":"ERR-P-001","message":"missing header"}}""");

        var ex = await Assert.ThrowsAsync<MissingHeaderException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Contains("missing header", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Status422_WithBusinessError_RaisesTypedSubclass()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.UnprocessableEntity,
            """{"error":{"code":"ERR-B-006","message":"NID failed"}}""");

        await Assert.ThrowsAsync<NationalIdInvalidException>(
            () => Send(Operation.SubmitClaim, "{}"));
    }

    [Fact]
    public async Task Status400_WithUnknownCode_FallsBackToProtocolException()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.BadRequest,
            """{"error":{"code":"ERR-P-042","message":"future"}}""");

        var ex = await Assert.ThrowsAsync<ProtocolException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Equal("ERR-P-042", ex.Code);
    }

    [Fact]
    public async Task Status400_WithUnparseableBody_FallsBackToUnknownBusiness()
    {
        _gatewayStub.EnqueueStatus(HttpStatusCode.BadRequest, "not-json");

        var ex = await Assert.ThrowsAsync<UnknownBusinessException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Equal("ERR-B-012", ex.Code);
    }

    [Fact]
    public async Task NetworkError_RetriesUntilSuccess()
    {
        _gatewayStub.EnqueueException(new HttpRequestException("connect refused"));
        _gatewayStub.EnqueueStatus(HttpStatusCode.Accepted, "{}");

        var resp = await Send(Operation.SubmitClaim, "{}");
        Assert.Equal(Status.Accepted, resp.Status);
        Assert.Equal(2, _gatewayStub.CallCount);
    }

    [Fact]
    public async Task NetworkError_ExhaustsRetries_RaisesTransport()
    {
        for (var i = 0; i < 4; i++)
        {
            _gatewayStub.EnqueueException(new HttpRequestException("boom"));
        }

        var ex = await Assert.ThrowsAsync<TransportException>(
            () => Send(Operation.SubmitClaim, "{}"));
        Assert.Equal("ERR-T-001", ex.Code);
    }

    // ── Construction guards ────────────────────────────────────────

    [Fact]
    public void Constructor_RejectsNullGatewayUrl()
    {
        Assert.Throws<ArgumentNullException>(() => new HfcxClient(
            gatewayUrl: null!,
            participantCode: "x",
            keycloak: _keycloak,
            encryptor: _encryptor));
    }

    [Fact]
    public void Constructor_RejectsEmptyParticipantCode()
    {
        Assert.ThrowsAny<ArgumentException>(() => new HfcxClient(
            gatewayUrl: GatewayUrl,
            participantCode: "",
            keycloak: _keycloak,
            encryptor: _encryptor));
    }

    [Fact]
    public void Constructor_RejectsNullKeycloak()
    {
        Assert.Throws<ArgumentNullException>(() => new HfcxClient(
            gatewayUrl: GatewayUrl,
            participantCode: "x",
            keycloak: null!,
            encryptor: _encryptor));
    }

    [Fact]
    public void Constructor_RejectsNullEncryptor()
    {
        Assert.Throws<ArgumentNullException>(() => new HfcxClient(
            gatewayUrl: GatewayUrl,
            participantCode: "x",
            keycloak: _keycloak,
            encryptor: null!));
    }

    private async Task<HfcxResponse> Send(Operation op, string payload)
    {
        return op switch
        {
            Operation.CheckEligibility => await _client.CheckEligibilityAsync(
                new CheckEligibilityRequest("payerco@hcx-egypt", payload)),
            Operation.SubmitPreauth => await _client.SubmitPreauthAsync(
                new SubmitPreauthRequest("payerco@hcx-egypt", payload)),
            Operation.SubmitClaim => await _client.SubmitClaimAsync(
                new SubmitClaimRequest("payerco@hcx-egypt", payload)),
            Operation.SendCommunication => await _client.SendCommunicationAsync(
                new SendCommunicationRequest("payerco@hcx-egypt", payload)),
            Operation.NotifyPayment => await _client.NotifyPaymentAsync(
                new NotifyPaymentRequest("payerco@hcx-egypt", payload)),
            _ => throw new InvalidOperationException($"unknown {op}"),
        };
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
}
