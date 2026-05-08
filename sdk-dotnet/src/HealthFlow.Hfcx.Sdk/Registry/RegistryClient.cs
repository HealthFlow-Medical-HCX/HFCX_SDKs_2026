// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace HealthFlow.Hfcx.Sdk.Registry;

/// <summary>
/// Caffeine-cached lookup of HFCX participant encryption certs against
/// the platform's Sunbird-RC participant registry.
/// </summary>
/// <remarks>
/// <para>Sister to the Java SDK's <c>RegistryClient</c> and the Python
/// SDK's <c>AsyncRegistryClient</c>. Cross-SDK invariants:</para>
/// <list type="bullet">
///   <item>Per-entry TTL = cert <c>notAfter - PreExpiryBuffer</c>
///     (default 1h).</item>
///   <item>Bounded by an LRU cache; default 10 000 entries.</item>
///   <item>404 → <see cref="ParticipantNotFoundException"/>.</item>
///   <item>5xx / network failures → <see cref="RegistryUnavailableException"/>.</item>
///   <item>Malformed JSON / PEM / non-RSA cert →
///     <see cref="TransportException"/>.</item>
///   <item>Hit / miss / eviction stats logged at INFO at most every 60s.</item>
/// </list>
/// </remarks>
public sealed class RegistryClient : IRecipientCertResolver, IDisposable
{
    /// <summary>Default time before <c>notAfter</c> to evict — 1 hour.</summary>
    public static readonly TimeSpan DefaultPreExpiryBuffer = TimeSpan.FromHours(1);

    /// <summary>Default LRU cache capacity.</summary>
    public const int DefaultMaxEntries = 10_000;

    private readonly Uri _baseUrl;
    private readonly HttpClient _httpClient;
    private readonly bool _ownsHttpClient;
    private readonly TimeSpan _preExpiryBuffer;
    private readonly MemoryCache _cache;
    private readonly Func<DateTimeOffset> _clock;
    private readonly ILogger _log;
    private readonly object _statsLock = new();
    private long _hits;
    private long _misses;
    private long _evictions;
    private DateTimeOffset _lastStatsLog;

    /// <summary>
    /// Construct a <see cref="RegistryClient"/>.
    /// </summary>
    /// <param name="baseUrl">
    /// Sunbird-RC participant-registry root, e.g.
    /// <c>https://registry.hcx-egypt.gov.eg</c>. Lookup paths are
    /// appended at request time.
    /// </param>
    /// <param name="httpClient">
    /// Optional injected <see cref="HttpClient"/>. When omitted the
    /// client owns and disposes a fresh instance.
    /// </param>
    /// <param name="preExpiryBuffer">
    /// Time before <c>notAfter</c> to evict an entry. Default 1h.
    /// </param>
    /// <param name="maxEntries">LRU bound. Default 10 000.</param>
    /// <param name="logger">Optional logger.</param>
    /// <param name="clock">Test seam for the wall clock.</param>
    public RegistryClient(
        Uri baseUrl,
        HttpClient? httpClient = null,
        TimeSpan? preExpiryBuffer = null,
        int? maxEntries = null,
        ILogger<RegistryClient>? logger = null,
        Func<DateTimeOffset>? clock = null)
    {
        ArgumentNullException.ThrowIfNull(baseUrl);

        _baseUrl = baseUrl;
        _preExpiryBuffer = preExpiryBuffer ?? DefaultPreExpiryBuffer;

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

        var cap = maxEntries ?? DefaultMaxEntries;
        _cache = new MemoryCache(new MemoryCacheOptions
        {
            SizeLimit = cap,
            CompactionPercentage = 0.1,
        });
        _log = logger ?? NullLogger<RegistryClient>.Instance;
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _lastStatsLog = _clock();
    }

    /// <inheritdoc />
    public async Task<ParticipantCert> GetRecipientCertAsync(
        string participantCode,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrEmpty(participantCode);

        if (_cache.TryGetValue<ParticipantCert>(participantCode, out var cached) && cached is not null)
        {
            Interlocked.Increment(ref _hits);
            MaybeLogStats();
            return cached;
        }

        Interlocked.Increment(ref _misses);

        var fetched = await FetchAsync(participantCode, cancellationToken).ConfigureAwait(false);
        var ttl = TtlFor(fetched);
        if (ttl <= TimeSpan.Zero)
        {
            // Cert is already inside its pre-expiry buffer — return it but
            // do not cache; the next call will fetch a fresh one.
            return fetched;
        }

        _cache.Set(
            participantCode,
            fetched,
            new MemoryCacheEntryOptions
            {
                Size = 1,
                AbsoluteExpirationRelativeToNow = ttl,
                PostEvictionCallbacks =
                {
                    new PostEvictionCallbackRegistration
                    {
                        EvictionCallback = (_, _, reason, _) =>
                        {
                            if (reason is EvictionReason.Capacity or EvictionReason.Expired)
                            {
                                Interlocked.Increment(ref _evictions);
                            }
                        },
                    },
                },
            });
        MaybeLogStats();
        return fetched;
    }

    /// <summary>Drop the cached entry for one participant.</summary>
    public void Invalidate(string participantCode)
    {
        ArgumentException.ThrowIfNullOrEmpty(participantCode);
        _cache.Remove(participantCode);
    }

    /// <summary>Drop every cached entry. Idempotent.</summary>
    public void InvalidateAll()
    {
        _cache.Compact(1.0);
    }

    private TimeSpan TtlFor(ParticipantCert cert)
        => cert.NotAfter - _preExpiryBuffer - _clock();

    private async Task<ParticipantCert> FetchAsync(
        string participantCode,
        CancellationToken cancellationToken)
    {
        var path = $"/api/v1/Participant/search";
        var requestUri = new Uri(_baseUrl, path);

        HttpResponseMessage? response = null;
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, requestUri);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            request.Content = new StringContent(
                $"{{\"filters\":{{\"participant_code\":{{\"eq\":\"{participantCode}\"}}}}}}",
                System.Text.Encoding.UTF8,
                "application/json");

            response = await _httpClient
                .SendAsync(request, HttpCompletionOption.ResponseContentRead, cancellationToken)
                .ConfigureAwait(false);

            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                throw new ParticipantNotFoundException(
                    $"Registry has no entry for {participantCode}");
            }

            if ((int)response.StatusCode >= 500)
            {
                throw new RegistryUnavailableException(
                    $"Registry returned HTTP {(int)response.StatusCode} for {participantCode}");
            }

            if (!response.IsSuccessStatusCode)
            {
                throw new TransportException(
                    $"Registry returned HTTP {(int)response.StatusCode} for {participantCode}");
            }

            var body = await response.Content
                .ReadAsStringAsync(cancellationToken)
                .ConfigureAwait(false);
            return ParseRegistryEntry(participantCode, body);
        }
        catch (HttpRequestException ex)
        {
            throw new RegistryUnavailableException(
                $"Registry unreachable for {participantCode}: {ex.Message}");
        }
        catch (TaskCanceledException ex) when (!cancellationToken.IsCancellationRequested)
        {
            throw new RegistryUnavailableException(
                $"Registry request for {participantCode} timed out: {ex.Message}");
        }
        finally
        {
            response?.Dispose();
        }
    }

    private static ParticipantCert ParseRegistryEntry(string participantCode, string body)
    {
        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(body);
        }
        catch (JsonException ex)
        {
            throw new TransportException(
                $"Registry response for {participantCode} was not valid JSON: {ex.Message}");
        }

        using (doc)
        {
            JsonElement entries;
            if (doc.RootElement.ValueKind == JsonValueKind.Array)
            {
                entries = doc.RootElement;
            }
            else if (doc.RootElement.TryGetProperty("entity", out var entity)
                && entity.ValueKind == JsonValueKind.Array)
            {
                entries = entity;
            }
            else
            {
                throw new ParticipantNotFoundException(
                    $"Registry response had no entry for {participantCode}");
            }

            if (entries.GetArrayLength() == 0)
            {
                throw new ParticipantNotFoundException(
                    $"Registry response had no entry for {participantCode}");
            }

            var first = entries[0];
            if (!first.TryGetProperty("encryption_cert", out var certElement)
                || certElement.ValueKind != JsonValueKind.String)
            {
                throw new TransportException(
                    $"Registry entry for {participantCode} has no encryption_cert");
            }

            var pem = certElement.GetString()!;
            return ParseCert(participantCode, pem);
        }
    }

    private static ParticipantCert ParseCert(string participantCode, string pem)
    {
        X509Certificate2 cert;
        try
        {
            cert = X509Certificate2.CreateFromPem(pem);
        }
        catch (CryptographicException ex)
        {
            throw new TransportException(
                $"Failed to parse encryption_cert PEM for {participantCode}: {ex.Message}");
        }

        try
        {
            var rsa = cert.GetRSAPublicKey();
            if (rsa is null)
            {
                cert.Dispose();
                throw new TransportException(
                    $"encryption_cert for {participantCode} is not RSA");
            }

            return new ParticipantCert(
                ParticipantCode: participantCode,
                PublicKey: rsa,
                NotAfter: cert.NotAfter.ToUniversalTime());
        }
        finally
        {
            cert.Dispose();
        }
    }

    private void MaybeLogStats()
    {
        DateTimeOffset now;
        bool shouldLog;
        long hits, misses, evictions;

        lock (_statsLock)
        {
            now = _clock();
            shouldLog = (now - _lastStatsLog) >= TimeSpan.FromSeconds(60);
            if (shouldLog)
            {
                _lastStatsLog = now;
            }

            hits = Interlocked.Read(ref _hits);
            misses = Interlocked.Read(ref _misses);
            evictions = Interlocked.Read(ref _evictions);
        }

        if (shouldLog)
        {
            _log.LogInformation(
                "registry cache stats: hits={Hits} misses={Misses} evictions={Evictions}",
                hits,
                misses,
                evictions);
        }
    }

    /// <inheritdoc />
    public void Dispose()
    {
        _cache.Dispose();
        if (_ownsHttpClient)
        {
            _httpClient.Dispose();
        }
    }
}
