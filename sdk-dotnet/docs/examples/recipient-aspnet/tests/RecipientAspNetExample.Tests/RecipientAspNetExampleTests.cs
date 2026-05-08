// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Globalization;
using System.Net;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Client;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Examples.RecipientAspNet;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Protocol;
using HealthFlow.Hfcx.Sdk.Recipient;
using HealthFlow.Hfcx.Sdk.Registry;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Examples.RecipientAspNet.Tests;

/// <summary>
/// Boots the ASP.NET Core recipient app on a free port and posts a real
/// JWE-encrypted claim. Sister to Python's
/// <c>tests/test_recipient_app.py</c> under <c>recipient-fastapi</c> /
/// <c>recipient-flask</c> and Java's <c>RecipientApplicationTest</c>.
/// </summary>
public class RecipientAspNetExampleTests : IAsyncLifetime, IDisposable
{
    private const string LocalParticipant = "payerco@hcx-egypt";
    private const string RemoteParticipant = "myhospital@hcx-egypt";

    private RSA _rsa = null!;
    private OutboundEncryptor _encryptor = null!;
    private Microsoft.AspNetCore.Builder.WebApplication _app = null!;
    private string _baseUrl = null!;
    private HttpClient _http = null!;

    public async Task InitializeAsync()
    {
        _rsa = RSA.Create(2048);
        _encryptor = new OutboundEncryptor(new StaticResolver(LocalParticipant, _rsa));

        var handler = new RecipientHandler(
            keyProvider: new StaticKeyProvider(_rsa),
            localParticipantCode: LocalParticipant,
            bearerTokenValidator: new StubBearerValidator());

        var port = FreePort();
        _baseUrl = $"http://127.0.0.1:{port}";

        _app = RecipientApp.BuildApp(handler, []);
        _app.Urls.Add(_baseUrl);
        await _app.StartAsync().ConfigureAwait(false);

        _http = new HttpClient { BaseAddress = new Uri(_baseUrl) };
    }

    public async Task DisposeAsync()
    {
        await _app.StopAsync().ConfigureAwait(false);
        await _app.DisposeAsync().ConfigureAwait(false);
    }

    public void Dispose()
    {
        _http.Dispose();
        _rsa.Dispose();
    }

    private static int FreePort()
    {
        using var listener = new TcpListener(System.Net.IPAddress.Loopback, 0);
        listener.Start();
        var port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    private static string ValidBundle() =>
        """
        {"resourceType":"Bundle","type":"collection","entry":[
          {"resource":{
            "resourceType":"Patient",
            "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112355"}],
            "address":[{"country":"EG"}]
          }}
        ]}
        """;

    private async Task<HttpRequestMessage> BuildRequestAsync(
        string path,
        string payload,
        string? authorization = "Bearer test",
        string? recipientOverride = null,
        string? correlationId = null)
    {
        var jwe = await _encryptor.EncryptAsync(payload, LocalParticipant).ConfigureAwait(false);
        var envelope = $"{{\"payload\":\"{jwe}\"}}";

        var msg = new HttpRequestMessage(HttpMethod.Post, path)
        {
            Content = new StringContent(envelope, System.Text.Encoding.UTF8, "application/json"),
        };
        msg.Headers.TryAddWithoutValidation(ProtocolHeaders.SenderCode, RemoteParticipant);
        msg.Headers.TryAddWithoutValidation(ProtocolHeaders.RecipientCode, recipientOverride ?? LocalParticipant);
        msg.Headers.TryAddWithoutValidation(ProtocolHeaders.CorrelationId, correlationId ?? Guid.NewGuid().ToString());
        msg.Headers.TryAddWithoutValidation(
            ProtocolHeaders.Timestamp,
            DateTimeOffset.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ", CultureInfo.InvariantCulture));
        msg.Headers.TryAddWithoutValidation(ProtocolHeaders.ApiCallId, Guid.NewGuid().ToString());
        if (authorization is not null)
        {
            msg.Headers.TryAddWithoutValidation("Authorization", authorization);
        }

        return msg;
    }

    [Fact]
    public async Task PostsJweEncryptedClaim_Returns202()
    {
        var correlationId = Guid.NewGuid().ToString();
        using var msg = await BuildRequestAsync("/v1/claim/submit", ValidBundle(), correlationId: correlationId);
        using var response = await _http.SendAsync(msg);

        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<AcceptedBody>();
        Assert.NotNull(body);
        Assert.Equal(correlationId, body!.CorrelationId);
        Assert.Equal("accepted", body.Status);
    }

    [Fact]
    public async Task MissingBearer_Returns401()
    {
        using var msg = await BuildRequestAsync("/v1/claim/submit", ValidBundle(), authorization: null);
        using var response = await _http.SendAsync(msg);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ErrorBody>();
        Assert.Equal("ERR-T-002", body!.Error.Code);
    }

    [Fact]
    public async Task NonBundlePayload_Returns422()
    {
        using var msg = await BuildRequestAsync("/v1/claim/submit", """{"resourceType":"Patient"}""");
        using var response = await _http.SendAsync(msg);

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ErrorBody>();
        Assert.StartsWith("ERR-B-", body!.Error.Code, StringComparison.Ordinal);
    }

    [Fact]
    public async Task RecipientMismatch_Returns400()
    {
        using var msg = await BuildRequestAsync(
            "/v1/claim/submit", ValidBundle(), recipientOverride: "someoneelse@hcx-egypt");
        using var response = await _http.SendAsync(msg);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ErrorBody>();
        Assert.StartsWith("ERR-P-", body!.Error.Code, StringComparison.Ordinal);
    }

    [Fact]
    public async Task AllFiveEndpoints_AcceptValidPost()
    {
        foreach (var path in RecipientApp.HfcxPaths)
        {
            using var msg = await BuildRequestAsync(path, ValidBundle());
            using var response = await _http.SendAsync(msg);
            Assert.True(
                response.StatusCode == HttpStatusCode.Accepted,
                $"{path}: {(int)response.StatusCode} {await response.Content.ReadAsStringAsync()}");
        }
    }

    private sealed class StaticKeyProvider : ILocalKeyProvider
    {
        private readonly RSA _key;

        public StaticKeyProvider(RSA key) => _key = key;

        public RSA GetPrivateKey()
        {
            var clone = RSA.Create();
            clone.ImportParameters(_key.ExportParameters(includePrivateParameters: true));
            return clone;
        }
    }

    private sealed class StubBearerValidator : IBearerTokenValidator
    {
        public void Validate(string? authorizationHeader)
        {
            if (string.IsNullOrEmpty(authorizationHeader)
                || !authorizationHeader.StartsWith("Bearer ", StringComparison.Ordinal))
            {
                throw new AuthenticationException("missing bearer");
            }
        }
    }

    private sealed class StaticResolver : IRecipientCertResolver
    {
        private readonly ParticipantCert _cert;

        public StaticResolver(string code, RSA key)
        {
            _cert = new ParticipantCert(code, key, DateTimeOffset.UtcNow.AddHours(2));
        }

        public Task<ParticipantCert> GetRecipientCertAsync(
            string participantCode,
            CancellationToken cancellationToken = default)
            => Task.FromResult(_cert);
    }

    private sealed record AcceptedBody(
        [property: System.Text.Json.Serialization.JsonPropertyName("correlation_id")] string CorrelationId,
        [property: System.Text.Json.Serialization.JsonPropertyName("status")] string Status);

    private sealed record ErrorBody(
        [property: System.Text.Json.Serialization.JsonPropertyName("error")] ErrorPayload Error);

    private sealed record ErrorPayload(
        [property: System.Text.Json.Serialization.JsonPropertyName("code")] string Code,
        [property: System.Text.Json.Serialization.JsonPropertyName("message")] string Message);
}
