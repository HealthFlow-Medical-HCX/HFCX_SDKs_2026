// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Logging;
using HealthFlow.Hfcx.Sdk.Protocol;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// High-level HFCX sender client. Async-only — .NET convention.
/// </summary>
/// <remarks>
/// <para>Sister to Java's <c>HfcxClient</c> and Python's
/// <c>AsyncHfcxClient</c>. Each sender method runs the full outbound
/// flow: registry lookup → JWE encryption → bearer-token auth → POST
/// to the gateway, with 1s/2s/4s exponential backoff on 5xx (max 4
/// attempts) and typed-exception mapping for 4xx via
/// <see cref="HfcxException.FromWireCode"/>.</para>
///
/// <para>HTTP 202 → <see cref="Status.Accepted"/>; HTTP 401
/// invalidates the cached bearer and raises
/// <see cref="AuthenticationException"/>; 4xx with a parseable
/// <c>error.code</c> body is routed to the right typed subclass; 4xx
/// without a parseable body falls back to
/// <see cref="UnknownBusinessException"/>.</para>
/// </remarks>
public sealed class HfcxClient : IDisposable
{
    /// <summary>Default request timeout — 30 seconds.</summary>
    public static readonly TimeSpan DefaultRequestTimeout = TimeSpan.FromSeconds(30);

    /// <summary>Default retry delays: 1s / 2s / 4s (max 4 attempts).</summary>
    public static readonly IReadOnlyList<TimeSpan> DefaultRetryDelays =
    [
        TimeSpan.FromSeconds(1),
        TimeSpan.FromSeconds(2),
        TimeSpan.FromSeconds(4),
    ];

    private readonly Uri _gatewayUrl;
    private readonly string _participantCode;
    private readonly KeycloakTokenClient _keycloak;
    private readonly OutboundEncryptor _encryptor;
    private readonly HttpClient _httpClient;
    private readonly bool _ownsHttpClient;
    private readonly IReadOnlyDictionary<Operation, string> _endpoints;
    private readonly TimeSpan _requestTimeout;
    private readonly IReadOnlyList<TimeSpan> _retryDelays;
    private readonly Func<DateTimeOffset> _clock;
    private readonly Func<string> _correlationIdGenerator;
    private readonly Func<string> _apiCallIdGenerator;
    private readonly Func<TimeSpan, CancellationToken, Task> _delay;
    private readonly ILogger _log;

    /// <summary>
    /// Construct a new <see cref="HfcxClient"/>.
    /// </summary>
    public HfcxClient(
        Uri gatewayUrl,
        string participantCode,
        KeycloakTokenClient keycloak,
        OutboundEncryptor encryptor,
        HttpClient? httpClient = null,
        IReadOnlyDictionary<Operation, string>? endpoints = null,
        TimeSpan? requestTimeout = null,
        IReadOnlyList<TimeSpan>? retryDelays = null,
        ILogger<HfcxClient>? logger = null,
        Func<DateTimeOffset>? clock = null,
        Func<string>? correlationIdGenerator = null,
        Func<string>? apiCallIdGenerator = null,
        Func<TimeSpan, CancellationToken, Task>? delay = null)
    {
        ArgumentNullException.ThrowIfNull(gatewayUrl);
        ArgumentException.ThrowIfNullOrEmpty(participantCode);
        ArgumentNullException.ThrowIfNull(keycloak);
        ArgumentNullException.ThrowIfNull(encryptor);

        _gatewayUrl = StripTrailingSlash(gatewayUrl);
        _participantCode = participantCode;
        _keycloak = keycloak;
        _encryptor = encryptor;
        _endpoints = endpoints ?? DefaultEndpoints.Map;
        _requestTimeout = requestTimeout ?? DefaultRequestTimeout;
        _retryDelays = retryDelays ?? DefaultRetryDelays;

        if (httpClient is null)
        {
            _httpClient = new HttpClient { Timeout = _requestTimeout };
            _ownsHttpClient = true;
        }
        else
        {
            _httpClient = httpClient;
            _ownsHttpClient = false;
        }

        _log = logger ?? NullLogger<HfcxClient>.Instance;
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _correlationIdGenerator = correlationIdGenerator ?? (() => Guid.NewGuid().ToString());
        _apiCallIdGenerator = apiCallIdGenerator ?? (() => Guid.NewGuid().ToString());
        _delay = delay ?? Task.Delay;
    }

    // ── Five typed sender methods ──────────────────────────────────

    public Task<HfcxResponse> CheckEligibilityAsync(
        CheckEligibilityRequest request,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        return DispatchAsync(request, cancellationToken);
    }

    public Task<HfcxResponse> SubmitPreauthAsync(
        SubmitPreauthRequest request,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        return DispatchAsync(request, cancellationToken);
    }

    public Task<HfcxResponse> SubmitClaimAsync(
        SubmitClaimRequest request,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        return DispatchAsync(request, cancellationToken);
    }

    public Task<HfcxResponse> SendCommunicationAsync(
        SendCommunicationRequest request,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        return DispatchAsync(request, cancellationToken);
    }

    public Task<HfcxResponse> NotifyPaymentAsync(
        NotifyPaymentRequest request,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        return DispatchAsync(request, cancellationToken);
    }

    private async Task<HfcxResponse> DispatchAsync(
        IHfcxRequest request,
        CancellationToken cancellationToken)
    {
        var operation = request.Operation;
        if (!_endpoints.TryGetValue(operation, out var path))
        {
            throw new ArgumentException(
                $"No endpoint configured for operation {operation}");
        }

        var correlationId = string.IsNullOrEmpty(request.CorrelationId)
            ? _correlationIdGenerator()
            : request.CorrelationId;
        var apiCallId = _apiCallIdGenerator();
        var endpoint = new Uri(_gatewayUrl, path);

        using (CorrelationId.Scope(correlationId))
        {
            _log.LogInformation(
                "hfcx.{Operation} starting recipient_code={RecipientCode} api_call_id={ApiCallId}",
                operation,
                request.RecipientCode,
                apiCallId);

            var jwe = await _encryptor
                .EncryptAsync(request.PayloadBundle, request.RecipientCode, cancellationToken)
                .ConfigureAwait(false);

            var envelope = BuildEnvelope(jwe);
            var protoHeaders = ProtocolHeaders.Build(
                _participantCode,
                request.RecipientCode,
                correlationId,
                _clock(),
                apiCallId);

            var bearer = await _keycloak.GetTokenAsync(cancellationToken).ConfigureAwait(false);

            using var response = await PostWithRetryAsync(
                    endpoint, envelope, bearer, protoHeaders, operation, cancellationToken)
                .ConfigureAwait(false);

            return await MapResponseAsync(response, correlationId, operation, cancellationToken)
                .ConfigureAwait(false);
        }
    }

    private async Task<HttpResponseMessage> PostWithRetryAsync(
        Uri endpoint,
        string envelopeJson,
        string bearer,
        IReadOnlyDictionary<string, string> protoHeaders,
        Operation operation,
        CancellationToken cancellationToken)
    {
        Exception? lastTransient = null;

        for (var attempt = 0; attempt <= _retryDelays.Count; attempt++)
        {
            HttpResponseMessage? response = null;
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Post, endpoint)
                {
                    Content = new StringContent(envelopeJson, Encoding.UTF8, "application/json"),
                };
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", bearer);
                request.Headers.UserAgent.ParseAdd(BuildUserAgent());
                foreach (var (key, value) in protoHeaders)
                {
                    request.Headers.TryAddWithoutValidation(key, value);
                }

                response = await _httpClient
                    .SendAsync(request, HttpCompletionOption.ResponseContentRead, cancellationToken)
                    .ConfigureAwait(false);

                var status = (int)response.StatusCode;
                if (status >= 500 && status < 600)
                {
                    if (attempt < _retryDelays.Count)
                    {
                        _log.LogWarning(
                            "gateway returned HTTP {Status} (attempt {Attempt}/{Max}); retrying in {Delay}",
                            status,
                            attempt + 1,
                            _retryDelays.Count + 1,
                            _retryDelays[attempt]);
                        response.Dispose();
                        await _delay(_retryDelays[attempt], cancellationToken).ConfigureAwait(false);
                        continue;
                    }

                    throw new Gateway5xxException(
                        $"Gateway returned HTTP {status} after {_retryDelays.Count + 1} attempts for {operation}");
                }

                return response;
            }
            catch (HttpRequestException ex)
            {
                response?.Dispose();
                lastTransient = ex;
            }
            catch (TaskCanceledException ex) when (!cancellationToken.IsCancellationRequested)
            {
                response?.Dispose();
                lastTransient = ex;
            }

            if (attempt < _retryDelays.Count)
            {
                _log.LogWarning(
                    lastTransient,
                    "gateway request failed; retrying in {Delay}",
                    _retryDelays[attempt]);
                await _delay(_retryDelays[attempt], cancellationToken).ConfigureAwait(false);
            }
            else
            {
                throw new TransportException(
                    $"Gateway request failed after {_retryDelays.Count + 1} attempts: {lastTransient?.Message}");
            }
        }

        throw new TransportException("unreachable: retry loop exited without a result");
    }

    private async Task<HfcxResponse> MapResponseAsync(
        HttpResponseMessage response,
        string correlationId,
        Operation operation,
        CancellationToken cancellationToken)
    {
        var status = (int)response.StatusCode;
        if (response.StatusCode == HttpStatusCode.Accepted)
        {
            _log.LogInformation("hfcx.{Operation} accepted by gateway (HTTP 202)", operation);
            return new HfcxResponse(correlationId, Status.Accepted);
        }

        if (response.StatusCode == HttpStatusCode.Unauthorized)
        {
            _keycloak.Invalidate();
            throw new AuthenticationException(
                $"Gateway rejected bearer token with HTTP 401 for {operation}");
        }

        if (status >= 400 && status < 500)
        {
            var body = await response.Content
                .ReadAsStringAsync(cancellationToken)
                .ConfigureAwait(false);
            throw Map4xxToTypedException(body, status, operation);
        }

        throw new TransportException(
            $"Gateway returned unexpected HTTP {status} for {operation}");
    }

    private static HfcxException Map4xxToTypedException(string? body, int status, Operation operation)
    {
        var code = UnknownBusinessException.CodeValue;
        var message = $"HTTP {status} from gateway for {operation}";

        if (!string.IsNullOrEmpty(body))
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                if (doc.RootElement.ValueKind == JsonValueKind.Object
                    && doc.RootElement.TryGetProperty("error", out var err)
                    && err.ValueKind == JsonValueKind.Object)
                {
                    if (err.TryGetProperty("code", out var codeNode)
                        && codeNode.ValueKind == JsonValueKind.String
                        && !string.IsNullOrEmpty(codeNode.GetString()))
                    {
                        code = codeNode.GetString()!;
                    }

                    if (err.TryGetProperty("message", out var msgNode)
                        && msgNode.ValueKind == JsonValueKind.String
                        && !string.IsNullOrEmpty(msgNode.GetString()))
                    {
                        message = msgNode.GetString()!;
                    }
                }
            }
            catch (JsonException)
            {
                // Body was 4xx but not JSON — fall through with the
                // unknown-business code, preserving the platform-reported
                // status in the message.
            }
        }

        return HfcxException.FromWireCode(code, message);
    }

    private static string BuildEnvelope(string jweCompact)
        => $"{{\"payload\":\"{jweCompact}\"}}";

    private static string BuildUserAgent()
        => $"hfcx-sdk-dotnet/{HfcxSdk.Version}";

    private static Uri StripTrailingSlash(Uri uri)
    {
        var s = uri.ToString();
        return s.EndsWith('/') ? new Uri(s.TrimEnd('/')) : uri;
    }

    /// <inheritdoc />
    public void Dispose()
    {
        if (_ownsHttpClient)
        {
            _httpClient.Dispose();
        }
    }
}
