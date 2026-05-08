// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Logging;
using HealthFlow.Hfcx.Sdk.Protocol;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Orchestrates the four-layer HFCX recipient pipeline:
/// <c>Bearer → Headers → Fhir → Egyptian</c>. Each layer is
/// independently toggleable.
/// </summary>
/// <remarks>
/// Sister to Java's <c>RecipientHandler</c> and Python's
/// <c>RecipientHandler</c>. The SDK does NOT ship a default
/// trust-everything <see cref="IBearerTokenValidator"/> — enabling
/// <see cref="Layer.Bearer"/> without configuring one fails fast at
/// construction.
/// </remarks>
public sealed class RecipientHandler
{
    private readonly IReadOnlySet<Layer> _enabledLayers;
    private readonly InboundDecryptor _decryptor;
    private readonly IBearerTokenValidator? _bearerValidator;
    private readonly HeaderValidator? _headerValidator;
    private readonly FhirValidator? _fhirValidator;
    private readonly EgyptianBundleValidator? _egyptianValidator;
    private readonly ILogger _log;

    /// <summary>Construct the handler. Pass <paramref name="enabledLayers"/> as null for all four.</summary>
    public RecipientHandler(
        ILocalKeyProvider keyProvider,
        string? localParticipantCode = null,
        IBearerTokenValidator? bearerTokenValidator = null,
        IReadOnlySet<Layer>? enabledLayers = null,
        Func<DateTimeOffset>? clock = null,
        TimeSpan? timestampTolerance = null,
        ILogger<RecipientHandler>? logger = null)
    {
        ArgumentNullException.ThrowIfNull(keyProvider);

        _enabledLayers = enabledLayers
            ?? new HashSet<Layer> { Layer.Bearer, Layer.Headers, Layer.Fhir, Layer.Egyptian };

        if (_enabledLayers.Contains(Layer.Headers) && string.IsNullOrEmpty(localParticipantCode))
        {
            throw new ArgumentException(
                "localParticipantCode is required when Layer.Headers is enabled",
                nameof(localParticipantCode));
        }

        if (_enabledLayers.Contains(Layer.Bearer) && bearerTokenValidator is null)
        {
            throw new ArgumentException(
                "Layer.Bearer is enabled but no IBearerTokenValidator was supplied. "
                + "The SDK does NOT ship a default trust-everything validator; "
                + "configure one or disable Layer.Bearer explicitly.",
                nameof(bearerTokenValidator));
        }

        _decryptor = new InboundDecryptor(keyProvider);
        _bearerValidator = bearerTokenValidator;
        _headerValidator = _enabledLayers.Contains(Layer.Headers)
            ? new HeaderValidator(localParticipantCode!, clock, timestampTolerance)
            : null;
        _fhirValidator = _enabledLayers.Contains(Layer.Fhir) ? new FhirValidator() : null;
        _egyptianValidator = _enabledLayers.Contains(Layer.Egyptian) ? new EgyptianBundleValidator() : null;
        _log = logger ?? NullLogger<RecipientHandler>.Instance;
    }

    /// <summary>The set of layers currently enabled.</summary>
    public IReadOnlySet<Layer> EnabledLayers => _enabledLayers;

    /// <summary>
    /// Run the pipeline against an inbound request. Throws the
    /// appropriate typed <see cref="HfcxException"/> on validation
    /// failure; returns <see cref="RecipientResult"/> on success.
    /// </summary>
    public RecipientResult Handle(
        string? authorizationHeader,
        IReadOnlyDictionary<string, string> protocolHeaders,
        string requestBody)
    {
        ArgumentNullException.ThrowIfNull(protocolHeaders);
        ArgumentNullException.ThrowIfNull(requestBody);

        var correlationId = protocolHeaders.TryGetValue(ProtocolHeaders.CorrelationId, out var cid)
            ? cid
            : "no-correlation-id";

        using (CorrelationId.Scope(correlationId))
        {
            _log.LogInformation(
                "recipient: handling inbound request with layers {Layers}",
                string.Join(",", _enabledLayers));

            if (_enabledLayers.Contains(Layer.Bearer))
            {
                _bearerValidator!.Validate(authorizationHeader);
            }

            if (_enabledLayers.Contains(Layer.Headers))
            {
                _headerValidator!.Validate(protocolHeaders);
            }

            var jwe = ExtractPayload(requestBody);
            var decrypted = _decryptor.Decrypt(jwe);

            _fhirValidator?.Validate(decrypted);
            _egyptianValidator?.Validate(decrypted);

            _log.LogInformation("recipient: accepted (all enabled layers passed)");
            return new RecipientResult(decrypted, protocolHeaders, correlationId);
        }
    }

    private static string ExtractPayload(string requestBody)
    {
        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(requestBody);
        }
        catch (JsonException ex)
        {
            throw new EnvelopeMalformedJsonException(
                $"Request body is not valid JSON: {ex.Message}");
        }

        using (doc)
        {
            if (doc.RootElement.ValueKind != JsonValueKind.Object)
            {
                throw new EnvelopeMissingPayloadException(
                    "Request body envelope is not a JSON object");
            }

            if (!doc.RootElement.TryGetProperty("payload", out var payload)
                || payload.ValueKind != JsonValueKind.String
                || string.IsNullOrEmpty(payload.GetString()))
            {
                throw new EnvelopeMissingPayloadException(
                    "Request body envelope missing required 'payload' field");
            }

            return payload.GetString()!;
        }
    }
}
