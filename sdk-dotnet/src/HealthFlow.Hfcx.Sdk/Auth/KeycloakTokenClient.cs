// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace HealthFlow.Hfcx.Sdk.Auth;

/// <summary>
/// Caches and refreshes Keycloak bearer tokens for the configured
/// <c>clientId</c> / <c>clientSecret</c>.
/// </summary>
/// <remarks>
/// <para>Sister to the Java SDK's <c>KeycloakTokenClient</c> and the
/// Python SDK's <c>AsyncKeycloakTokenClient</c>. Cross-SDK invariants:</para>
/// <list type="bullet">
///   <item>Tokens cached for <c>expires_in - RefreshLeadTime</c> seconds.</item>
///   <item>5xx responses retry with 1s/2s/4s exponential backoff (max 4 attempts).</item>
///   <item>401 surfaces immediately as
///     <see cref="AuthenticationException"/>; never retried.</item>
///   <item>Concurrent waiters collapse to a single HTTP fetch via
///     <see cref="SemaphoreSlim"/> double-checked locking.</item>
///   <item>Tokens are NEVER persisted to disk.</item>
/// </list>
/// </remarks>
public sealed class KeycloakTokenClient : IDisposable
{
    /// <summary>Default time before expiry to refresh proactively.</summary>
    public static readonly TimeSpan DefaultRefreshLeadTime = TimeSpan.FromSeconds(60);

    /// <summary>Default max attempts: 4 (initial + 3 retries).</summary>
    public const int DefaultMaxAttempts = 4;

    private readonly Uri _tokenUrl;
    private readonly string _clientId;
    private readonly string _clientSecret;
    private readonly TimeSpan _refreshLeadTime;
    private readonly int _maxAttempts;
    private readonly HttpClient _httpClient;
    private readonly bool _ownsHttpClient;
    private readonly ILogger _log;
    private readonly Func<DateTimeOffset> _clock;
    private readonly Func<int, TimeSpan> _backoff;
    private readonly SemaphoreSlim _gate = new(1, 1);

    private CachedToken? _cached;

    /// <summary>
    /// Construct a Keycloak token client.
    /// </summary>
    /// <param name="tokenUrl">
    /// Keycloak token endpoint URL, e.g.
    /// <c>https://idp/realms/hcx/protocol/openid-connect/token</c>.
    /// </param>
    /// <param name="clientId">Keycloak client ID.</param>
    /// <param name="clientSecret">Keycloak client secret.</param>
    /// <param name="httpClient">
    /// Optional injected <see cref="HttpClient"/>. When omitted the client
    /// owns and disposes a fresh instance.
    /// </param>
    /// <param name="refreshLeadTime">
    /// Time before <c>exp</c> to refresh proactively. Default 60s.
    /// </param>
    /// <param name="maxAttempts">
    /// Maximum HTTP attempts on transient failure. Default 4.
    /// </param>
    /// <param name="logger">Optional logger; defaults to a no-op.</param>
    /// <param name="clock">Test seam for the wall clock.</param>
    /// <param name="backoff">Test seam for the retry backoff schedule.</param>
    public KeycloakTokenClient(
        Uri tokenUrl,
        string clientId,
        string clientSecret,
        HttpClient? httpClient = null,
        TimeSpan? refreshLeadTime = null,
        int? maxAttempts = null,
        ILogger<KeycloakTokenClient>? logger = null,
        Func<DateTimeOffset>? clock = null,
        Func<int, TimeSpan>? backoff = null)
    {
        ArgumentNullException.ThrowIfNull(tokenUrl);
        ArgumentException.ThrowIfNullOrEmpty(clientId);
        ArgumentException.ThrowIfNullOrEmpty(clientSecret);

        _tokenUrl = tokenUrl;
        _clientId = clientId;
        _clientSecret = clientSecret;
        _refreshLeadTime = refreshLeadTime ?? DefaultRefreshLeadTime;
        _maxAttempts = maxAttempts ?? DefaultMaxAttempts;
        if (_maxAttempts < 1)
        {
            throw new ArgumentOutOfRangeException(
                nameof(maxAttempts), "must be >= 1");
        }

        if (httpClient is null)
        {
            _httpClient = new HttpClient();
            _ownsHttpClient = true;
        }
        else
        {
            _httpClient = httpClient;
            _ownsHttpClient = false;
        }

        _log = logger ?? NullLogger<KeycloakTokenClient>.Instance;
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _backoff = backoff ?? DefaultBackoff;
    }

    /// <summary>
    /// Return a cached token if still fresh, otherwise fetch a new one.
    /// </summary>
    public async Task<string> GetTokenAsync(CancellationToken cancellationToken = default)
    {
        var snapshot = _cached;
        if (snapshot is not null && !IsExpired(snapshot))
        {
            return snapshot.AccessToken;
        }

        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            // Double-check after acquiring the lock; another waiter may
            // have refreshed while we were queued.
            snapshot = _cached;
            if (snapshot is not null && !IsExpired(snapshot))
            {
                return snapshot.AccessToken;
            }

            var fetched = await FetchAsync(cancellationToken).ConfigureAwait(false);
            _cached = fetched;
            return fetched.AccessToken;
        }
        finally
        {
            _gate.Release();
        }
    }

    /// <summary>
    /// Drop the cached token. The next <see cref="GetTokenAsync"/> call
    /// fetches a fresh one. Idempotent; safe to call concurrently.
    /// </summary>
    public void Invalidate()
    {
        _cached = null;
    }

    private bool IsExpired(CachedToken token)
        => _clock() >= token.ExpiresAt - _refreshLeadTime;

    private async Task<CachedToken> FetchAsync(CancellationToken cancellationToken)
    {
        Exception? lastTransient = null;

        for (var attempt = 1; attempt <= _maxAttempts; attempt++)
        {
            HttpResponseMessage? response = null;
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Post, _tokenUrl);
                request.Content = new FormUrlEncodedContent(
                    new[]
                    {
                        new KeyValuePair<string, string>("grant_type", "client_credentials"),
                        new KeyValuePair<string, string>("client_id", _clientId),
                        new KeyValuePair<string, string>("client_secret", _clientSecret),
                    });
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

                response = await _httpClient
                    .SendAsync(request, HttpCompletionOption.ResponseContentRead, cancellationToken)
                    .ConfigureAwait(false);

                if (response.StatusCode == HttpStatusCode.Unauthorized)
                {
                    var body = await response.Content
                        .ReadAsStringAsync(cancellationToken)
                        .ConfigureAwait(false);
                    throw new AuthenticationException(
                        $"Keycloak rejected client credentials with HTTP 401: {Truncate(body, 200)}");
                }

                if ((int)response.StatusCode >= 500)
                {
                    var body = await response.Content
                        .ReadAsStringAsync(cancellationToken)
                        .ConfigureAwait(false);
                    lastTransient = new TransportException(
                        $"Keycloak returned HTTP {(int)response.StatusCode}: {Truncate(body, 200)}");
                }
                else if (!response.IsSuccessStatusCode)
                {
                    var body = await response.Content
                        .ReadAsStringAsync(cancellationToken)
                        .ConfigureAwait(false);
                    throw new TransportException(
                        $"Keycloak returned HTTP {(int)response.StatusCode}: {Truncate(body, 200)}");
                }
                else
                {
                    var json = await response.Content
                        .ReadAsStringAsync(cancellationToken)
                        .ConfigureAwait(false);
                    return ParseTokenResponse(json);
                }
            }
            catch (AuthenticationException)
            {
                throw;
            }
            catch (HttpRequestException ex)
            {
                lastTransient = ex;
            }
            catch (TaskCanceledException ex) when (!cancellationToken.IsCancellationRequested)
            {
                lastTransient = ex;
            }
            finally
            {
                response?.Dispose();
            }

            if (attempt < _maxAttempts)
            {
                _log.LogWarning(
                    lastTransient,
                    "Keycloak token fetch attempt {Attempt}/{Max} failed; retrying",
                    attempt,
                    _maxAttempts);
                await Task.Delay(_backoff(attempt), cancellationToken).ConfigureAwait(false);
            }
        }

        throw new TransportException(
            $"Keycloak token fetch failed after {_maxAttempts} attempts: {lastTransient?.Message}");
    }

    private CachedToken ParseTokenResponse(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (!root.TryGetProperty("access_token", out var accessToken)
                || accessToken.ValueKind != JsonValueKind.String)
            {
                throw new TransportException(
                    "Keycloak response is missing 'access_token'");
            }

            // expires_in may be int or string depending on the IdP; default
            // to 60s if absent so the cache still works defensively.
            var expiresIn = 60;
            if (root.TryGetProperty("expires_in", out var ein))
            {
                expiresIn = ein.ValueKind switch
                {
                    JsonValueKind.Number => ein.GetInt32(),
                    JsonValueKind.String when int.TryParse(ein.GetString(), out var n) => n,
                    _ => 60,
                };
            }

            return new CachedToken(
                AccessToken: accessToken.GetString()!,
                ExpiresAt: _clock().AddSeconds(expiresIn));
        }
        catch (JsonException ex)
        {
            throw new TransportException(
                $"Keycloak response is not valid JSON: {ex.Message}");
        }
    }

    private static TimeSpan DefaultBackoff(int attempt)
        => TimeSpan.FromSeconds(Math.Pow(2, attempt - 1)); // 1s, 2s, 4s

    private static string Truncate(string value, int max)
        => value.Length > max ? value[..max] + "…" : value;

    /// <inheritdoc />
    public void Dispose()
    {
        _gate.Dispose();
        if (_ownsHttpClient)
        {
            _httpClient.Dispose();
        }
    }

    private sealed record CachedToken(string AccessToken, DateTimeOffset ExpiresAt);
}
