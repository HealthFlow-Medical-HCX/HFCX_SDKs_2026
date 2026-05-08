// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Globalization;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Auth;
using HealthFlow.Hfcx.Sdk.Client;
using HealthFlow.Hfcx.Sdk.Crypto;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Protocol;
using HealthFlow.Hfcx.Sdk.Recipient;
using HealthFlow.Hfcx.Sdk.Registry;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class RecipientHandlerTests : IDisposable
{
    private const string LocalParticipant = "payerco@hcx-egypt";
    private const string RemoteParticipant = "myhospital@hcx-egypt";

    private readonly RSA _rsa;
    private readonly StaticKeyProvider _keyProvider;
    private readonly StubBearerValidator _bearer;
    private readonly OutboundEncryptor _encryptor;
    private readonly Func<DateTimeOffset> _clock;

    public RecipientHandlerTests()
    {
        _rsa = RSA.Create(2048);
        _keyProvider = new StaticKeyProvider(_rsa);
        _bearer = new StubBearerValidator();
        _encryptor = new OutboundEncryptor(new StaticResolver(LocalParticipant, _rsa));
        _clock = () => new DateTimeOffset(2026, 5, 8, 12, 0, 0, TimeSpan.Zero);
    }

    public void Dispose() => _rsa.Dispose();

    private RecipientHandler NewHandler(IReadOnlySet<Layer>? layers = null)
        => new(
            keyProvider: _keyProvider,
            localParticipantCode: LocalParticipant,
            bearerTokenValidator: _bearer,
            enabledLayers: layers,
            clock: _clock);

    private static string ValidBundle() =>
        """
        {
          "resourceType":"Bundle","type":"collection",
          "entry":[
            {"resource":{
              "resourceType":"Patient",
              "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112355"}],
              "address":[{"country":"EG"}]
            }}
          ]
        }
        """;

    private async Task<string> EnvelopeAsync(string payload) =>
        $"{{\"payload\":\"{await _encryptor.EncryptAsync(payload, LocalParticipant)}\"}}";

    private IReadOnlyDictionary<string, string> ValidHeaders(
        string? correlationId = null,
        string? recipientOverride = null,
        string? timestampOverride = null)
        => new Dictionary<string, string>
        {
            [ProtocolHeaders.SenderCode] = RemoteParticipant,
            [ProtocolHeaders.RecipientCode] = recipientOverride ?? LocalParticipant,
            [ProtocolHeaders.CorrelationId] = correlationId ?? Guid.NewGuid().ToString(),
            [ProtocolHeaders.Timestamp] = timestampOverride
                ?? _clock().ToString("yyyy-MM-ddTHH:mm:ssZ", CultureInfo.InvariantCulture),
            [ProtocolHeaders.ApiCallId] = Guid.NewGuid().ToString(),
        };

    // ── Happy path ─────────────────────────────────────────────────

    [Fact]
    public async Task EndToEnd_AllLayersEnabled_ReturnsResult()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());

        var result = handler.Handle("Bearer test", ValidHeaders(), envelope);

        Assert.Contains("\"resourceType\":\"Bundle\"", result.DecryptedPayload, StringComparison.Ordinal);
        Assert.NotNull(result.CorrelationId);
    }

    [Fact]
    public async Task AllLayersDisabled_StillDecrypts()
    {
        var handler = new RecipientHandler(
            keyProvider: _keyProvider,
            enabledLayers: new HashSet<Layer>());

        var envelope = await EnvelopeAsync(ValidBundle());
        var result = handler.Handle(null, ValidHeaders(), envelope);
        Assert.Contains("Bundle", result.DecryptedPayload, StringComparison.Ordinal);
    }

    [Fact]
    public void EnabledLayers_DefaultsToAllFour()
    {
        var handler = NewHandler();
        Assert.Equal(4, handler.EnabledLayers.Count);
    }

    // ── Bearer layer ───────────────────────────────────────────────

    [Fact]
    public async Task Bearer_MissingHeader_RaisesAuthenticationException()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());

        Assert.Throws<AuthenticationException>(
            () => handler.Handle(null, ValidHeaders(), envelope));
    }

    [Fact]
    public void Bearer_LayerEnabledButNoValidator_FailsAtConstruction()
    {
        Assert.ThrowsAny<ArgumentException>(() => new RecipientHandler(
            keyProvider: _keyProvider,
            localParticipantCode: LocalParticipant,
            bearerTokenValidator: null,
            enabledLayers: new HashSet<Layer> { Layer.Bearer, Layer.Headers }));
    }

    [Fact]
    public async Task Bearer_DisabledLayer_AcceptsMissingHeader()
    {
        var handler = new RecipientHandler(
            keyProvider: _keyProvider,
            localParticipantCode: LocalParticipant,
            bearerTokenValidator: null,
            enabledLayers: new HashSet<Layer> { Layer.Headers, Layer.Fhir, Layer.Egyptian },
            clock: _clock);

        var envelope = await EnvelopeAsync(ValidBundle());
        var result = handler.Handle(null, ValidHeaders(), envelope);
        Assert.NotNull(result);
    }

    // ── Headers layer ──────────────────────────────────────────────

    [Fact]
    public async Task Headers_RecipientMismatch_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());

        Assert.Throws<RecipientCodeMismatchException>(
            () => handler.Handle("Bearer test", ValidHeaders(recipientOverride: "someone-else"), envelope));
    }

    [Fact]
    public async Task Headers_BadCorrelationId_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());

        Assert.Throws<BadUuidException>(
            () => handler.Handle("Bearer test", ValidHeaders(correlationId: "not-a-uuid"), envelope));
    }

    [Fact]
    public async Task Headers_TimestampOutOfRange_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());
        var future = _clock().AddHours(1).ToString("yyyy-MM-ddTHH:mm:ssZ", CultureInfo.InvariantCulture);

        Assert.Throws<TimestampOutOfRangeException>(
            () => handler.Handle("Bearer test", ValidHeaders(timestampOverride: future), envelope));
    }

    [Fact]
    public async Task Headers_BadTimestamp_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());

        Assert.Throws<BadTimestampException>(
            () => handler.Handle("Bearer test", ValidHeaders(timestampOverride: "not a timestamp"), envelope));
    }

    [Fact]
    public async Task Headers_MissingHeader_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync(ValidBundle());
        var headers = new Dictionary<string, string>(ValidHeaders());
        headers.Remove(ProtocolHeaders.Timestamp);

        Assert.Throws<MissingHeaderException>(
            () => handler.Handle("Bearer test", headers, envelope));
    }

    [Fact]
    public void Headers_NoLocalParticipantCode_FailsAtConstruction()
    {
        Assert.ThrowsAny<ArgumentException>(() => new RecipientHandler(
            keyProvider: _keyProvider,
            localParticipantCode: null,
            bearerTokenValidator: _bearer,
            enabledLayers: new HashSet<Layer> { Layer.Bearer, Layer.Headers }));
    }

    // ── FHIR layer ─────────────────────────────────────────────────

    [Fact]
    public async Task Fhir_NonBundle_Raises()
    {
        var handler = NewHandler();
        var envelope = await EnvelopeAsync("""{"resourceType":"Patient"}""");

        Assert.Throws<NotABundleException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
    }

    [Fact]
    public async Task Fhir_PatientWithoutNationalId_Raises()
    {
        var handler = NewHandler();
        const string bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{"resourceType":"Patient","address":[{"country":"EG"}]}}
            ]}
            """;
        var envelope = await EnvelopeAsync(bundle);
        Assert.Throws<PatientMissingNationalIdException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
    }

    [Fact]
    public async Task Fhir_PatientNonEgyptian_Raises()
    {
        var handler = NewHandler();
        const string bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112355"}],
                "address":[{"country":"US"}]
              }}
            ]}
            """;
        var envelope = await EnvelopeAsync(bundle);
        Assert.Throws<PatientNonEgyptianException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
    }

    // ── Egyptian layer ─────────────────────────────────────────────

    [Fact]
    public async Task Egyptian_InvalidNationalId_Raises()
    {
        var handler = NewHandler();
        const string bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"99999999999991"}],
                "address":[{"country":"EG"}]
              }}
            ]}
            """;
        var envelope = await EnvelopeAsync(bundle);
        Assert.Throws<NationalIdInvalidException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
    }

    [Fact]
    public async Task Egyptian_InvalidPhone_Raises()
    {
        var handler = NewHandler();
        const string bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112355"}],
                "address":[{"country":"EG"}],
                "telecom":[{"system":"phone","value":"01312345678"}]
              }}
            ]}
            """;
        var envelope = await EnvelopeAsync(bundle);
        Assert.Throws<PhoneInvalidException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
    }

    // ── Envelope errors ────────────────────────────────────────────

    [Fact]
    public void EnvelopeMissingPayload_Raises()
    {
        var handler = NewHandler();
        Assert.Throws<EnvelopeMissingPayloadException>(
            () => handler.Handle("Bearer test", ValidHeaders(), "{}"));
    }

    [Fact]
    public void EnvelopeMalformedJson_Raises()
    {
        var handler = NewHandler();
        Assert.Throws<EnvelopeMalformedJsonException>(
            () => handler.Handle("Bearer test", ValidHeaders(), "not-json"));
    }

    // ── Layer toggling ─────────────────────────────────────────────

    [Fact]
    public async Task FhirLayerDisabled_AllowsNonBundle()
    {
        var handler = new RecipientHandler(
            keyProvider: _keyProvider,
            localParticipantCode: LocalParticipant,
            bearerTokenValidator: _bearer,
            enabledLayers: new HashSet<Layer> { Layer.Bearer, Layer.Headers, Layer.Egyptian },
            clock: _clock);

        var envelope = await EnvelopeAsync("""{"resourceType":"Patient"}""");
        // Egyptian layer no-ops on non-Bundle JSON since FhirValidator
        // would have run first; this ensures the handler doesn't crash.
        var result = handler.Handle("Bearer test", ValidHeaders(), envelope);
        Assert.Contains("Patient", result.DecryptedPayload, StringComparison.Ordinal);
    }

    [Fact]
    public async Task BusinessException_Catches_TypedSubclass()
    {
        // Cross-SDK contract: typed subclasses inherit BusinessException so
        // callers can `catch (BusinessException)` and still receive a
        // strongly-typed code on .Code.
        var handler = NewHandler();
        const string bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "address":[{"country":"EG"}]
              }}
            ]}
            """;
        var envelope = await EnvelopeAsync(bundle);
        var ex = Assert.Throws<PatientMissingNationalIdException>(
            () => handler.Handle("Bearer test", ValidHeaders(), envelope));
        Assert.IsAssignableFrom<BusinessException>(ex);
        Assert.Equal("ERR-B-004", ex.Code);
    }

    // ── Test doubles ───────────────────────────────────────────────

    private sealed class StaticKeyProvider : ILocalKeyProvider
    {
        private readonly RSA _key;

        public StaticKeyProvider(RSA key) => _key = key;

        public RSA GetPrivateKey()
        {
            // Return a clone so callers can dispose without breaking the
            // shared underlying key.
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
                throw new AuthenticationException("Missing or malformed Authorization header");
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
}
