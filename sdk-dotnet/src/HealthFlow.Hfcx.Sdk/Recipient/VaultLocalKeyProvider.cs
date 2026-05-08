// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Loads the recipient's RSA private key from a HashiCorp Vault KV v2
/// secret. Sister to Java's <c>VaultLocalKeyProvider</c> and Python's
/// <c>VaultLocalKeyProvider</c>. Cross-SDK invariants:
/// </summary>
/// <remarks>
/// <list type="bullet">
///   <item><c>GET /v1/{mount}/data/{path}</c> with the
///     <c>X-Vault-Token</c> header.</item>
///   <item>Optional <c>X-Vault-Namespace</c> for Vault Enterprise.</item>
///   <item>Configurable <c>secretField</c> defaults to <c>"value"</c>.</item>
///   <item>403 / 404 / missing field → <see cref="KeyUnavailableException"/>.</item>
/// </list>
/// </remarks>
public sealed class VaultLocalKeyProvider : ILocalKeyProvider, IDisposable
{
    /// <summary>Default mount path on the KV-v2 secrets engine.</summary>
    public const string DefaultMount = "secret";

    /// <summary>Default key inside the secret containing the PEM.</summary>
    public const string DefaultSecretField = "value";

    private readonly Uri _vaultBaseUrl;
    private readonly string _secretPath;
    private readonly string _token;
    private readonly string _mount;
    private readonly string _secretField;
    private readonly string? _namespace;
    private readonly HttpClient _httpClient;
    private readonly bool _ownsHttpClient;
    private readonly TimeSpan _requestTimeout;

    /// <summary>Construct a Vault-backed key provider.</summary>
    public VaultLocalKeyProvider(
        Uri vaultBaseUrl,
        string secretPath,
        string vaultToken,
        string secretMount = DefaultMount,
        string secretField = DefaultSecretField,
        string? vaultNamespace = null,
        HttpClient? httpClient = null,
        TimeSpan? requestTimeout = null)
    {
        ArgumentNullException.ThrowIfNull(vaultBaseUrl);
        ArgumentException.ThrowIfNullOrEmpty(secretPath);
        ArgumentException.ThrowIfNullOrEmpty(vaultToken);
        ArgumentException.ThrowIfNullOrEmpty(secretMount);
        ArgumentException.ThrowIfNullOrEmpty(secretField);

        _vaultBaseUrl = vaultBaseUrl;
        _secretPath = secretPath;
        _token = vaultToken;
        _mount = secretMount;
        _secretField = secretField;
        _namespace = vaultNamespace;
        _requestTimeout = requestTimeout ?? TimeSpan.FromSeconds(10);

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
    }

    /// <inheritdoc />
    public RSA GetPrivateKey()
    {
        var url = new Uri(_vaultBaseUrl, $"/v1/{_mount}/data/{_secretPath}");
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.TryAddWithoutValidation("X-Vault-Token", _token);
        if (!string.IsNullOrEmpty(_namespace))
        {
            request.Headers.TryAddWithoutValidation("X-Vault-Namespace", _namespace);
        }

        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        HttpResponseMessage? response;
        try
        {
            response = _httpClient.Send(request);
        }
        catch (HttpRequestException ex)
        {
            throw new KeyUnavailableException(
                $"Vault unreachable at '{url}': {ex.Message}");
        }

        try
        {
            if (response.StatusCode is HttpStatusCode.Forbidden or HttpStatusCode.NotFound)
            {
                throw new KeyUnavailableException(
                    $"Vault returned HTTP {(int)response.StatusCode} for '{_secretPath}'");
            }

            if (!response.IsSuccessStatusCode)
            {
                throw new KeyUnavailableException(
                    $"Vault returned HTTP {(int)response.StatusCode} for '{_secretPath}'");
            }

            string body;
            using (var stream = response.Content.ReadAsStream())
            using (var reader = new System.IO.StreamReader(stream))
            {
                body = reader.ReadToEnd();
            }

            var pem = ExtractField(body);

            var rsa = RSA.Create();
            try
            {
                rsa.ImportFromPem(pem);
                return rsa;
            }
            catch (Exception ex) when (ex is ArgumentException or CryptographicException)
            {
                rsa.Dispose();
                throw new KeyUnavailableException(
                    $"Vault secret at '{_secretPath}' did not parse as PKCS#8 PEM: {ex.Message}");
            }
        }
        finally
        {
            response.Dispose();
        }
    }

    private string ExtractField(string body)
    {
        try
        {
            using var doc = JsonDocument.Parse(body);
            if (!doc.RootElement.TryGetProperty("data", out var outerData)
                || !outerData.TryGetProperty("data", out var innerData)
                || !innerData.TryGetProperty(_secretField, out var fieldNode)
                || fieldNode.ValueKind != JsonValueKind.String)
            {
                throw new KeyUnavailableException(
                    $"Vault secret at '{_secretPath}' is missing required field '{_secretField}'");
            }

            return fieldNode.GetString()!;
        }
        catch (JsonException ex)
        {
            throw new KeyUnavailableException(
                $"Vault response for '{_secretPath}' was not valid JSON: {ex.Message}");
        }
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
