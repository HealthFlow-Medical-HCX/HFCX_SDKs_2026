// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;
using System.Collections.ObjectModel;

namespace HealthFlow.Hfcx.Sdk.Exceptions;

/// <summary>
/// Single source of truth for every error code the SDK raises. Cross-SDK
/// invariant: the wire-format codes here match the Java SDK's
/// <c>ErrorCode</c> enum and the Python SDK's <c>hfcx_sdk.exceptions.ErrorCode</c>
/// byte-for-byte. 27 entries: 9 protocol, 12 business, 6 technical.
/// </summary>
public sealed class ErrorCode
{
    // ── ERR-P-* — protocol / wire-format violations ─────────────────────
    public static readonly ErrorCode MissingHeader = new("ERR-P-001", Tier.Protocol, "Required protocol header missing or empty");
    public static readonly ErrorCode JweAlgorithmRejected = new("ERR-P-002", Tier.Protocol, "JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM");
    public static readonly ErrorCode RecipientCodeMismatch = new("ERR-P-003", Tier.Protocol, "x-hcx-recipient_code does not match this participant");
    public static readonly ErrorCode BadUuid = new("ERR-P-004", Tier.Protocol, "Header value is not a valid UUID");
    public static readonly ErrorCode BadTimestamp = new("ERR-P-005", Tier.Protocol, "x-hcx-timestamp is not a valid ISO-8601 instant");
    public static readonly ErrorCode TimestampOutOfRange = new("ERR-P-006", Tier.Protocol, "x-hcx-timestamp is outside the configured tolerance window");
    public static readonly ErrorCode SenderUnknown = new("ERR-P-007", Tier.Protocol, "Sender participant code is not registered");
    public static readonly ErrorCode BadEnvelope = new("ERR-P-008", Tier.Protocol, "Request body envelope is malformed");
    public static readonly ErrorCode SignatureVerificationFailed = new("ERR-P-009", Tier.Protocol, "Detached signature verification failed");

    // ── ERR-B-* — business / FHIR / Egyptian profile violations ─────────
    public static readonly ErrorCode ParticipantNotFound = new("ERR-B-001", Tier.Business, "Participant code not found in the registry");
    public static readonly ErrorCode NotABundle = new("ERR-B-002", Tier.Business, "Top-level FHIR resource is not a Bundle");
    public static readonly ErrorCode BundleMissingType = new("ERR-B-003", Tier.Business, "Bundle.type is required by the Egyptian IG");
    public static readonly ErrorCode PatientMissingNationalId = new("ERR-B-004", Tier.Business, "Patient resource missing the National-ID identifier slice");
    public static readonly ErrorCode PatientNonEgyptian = new("ERR-B-005", Tier.Business, "Patient.address[0].country must be 'EG'");
    public static readonly ErrorCode NationalIdInvalid = new("ERR-B-006", Tier.Business, "Egyptian National ID value fails structural validation");
    public static readonly ErrorCode PhoneInvalid = new("ERR-B-007", Tier.Business, "Egyptian mobile phone value is not in a recognised format");
    public static readonly ErrorCode IbanInvalid = new("ERR-B-008", Tier.Business, "Egyptian IBAN value fails the ISO 13616 mod-97 check");
    public static readonly ErrorCode BadFhirJson = new("ERR-B-009", Tier.Business, "FHIR payload is not valid JSON");
    public static readonly ErrorCode EnvelopeMissingPayload = new("ERR-B-010", Tier.Business, "Request body envelope is missing the 'payload' field");
    public static readonly ErrorCode EnvelopeMalformedJson = new("ERR-B-011", Tier.Business, "Request body is not valid JSON");
    public static readonly ErrorCode UnknownBusiness = new("ERR-B-012", Tier.Business, "Unspecified business-rule failure (gateway error code missing or unparseable)");

    // ── ERR-T-* — technical / transport failures ────────────────────────
    public static readonly ErrorCode Transport = new("ERR-T-001", Tier.Technical, "Transport-layer failure");
    public static readonly ErrorCode Authentication = new("ERR-T-002", Tier.Technical, "Authentication rejected by the identity provider");
    public static readonly ErrorCode RegistryUnavailable = new("ERR-T-003", Tier.Technical, "Participant registry is unreachable");
    public static readonly ErrorCode KeyUnavailable = new("ERR-T-004", Tier.Technical, "Recipient private key cannot be loaded");
    public static readonly ErrorCode CryptographicFailure = new("ERR-T-005", Tier.Technical, "JOSE library reported a cryptographic failure");
    public static readonly ErrorCode Gateway5xx = new("ERR-T-006", Tier.Technical, "HFCX gateway returned a 5xx response after retry exhaustion");

    private static readonly IReadOnlyList<ErrorCode> _all =
    [
        MissingHeader, JweAlgorithmRejected, RecipientCodeMismatch, BadUuid,
        BadTimestamp, TimestampOutOfRange, SenderUnknown, BadEnvelope,
        SignatureVerificationFailed,
        ParticipantNotFound, NotABundle, BundleMissingType, PatientMissingNationalId,
        PatientNonEgyptian, NationalIdInvalid, PhoneInvalid, IbanInvalid,
        BadFhirJson, EnvelopeMissingPayload, EnvelopeMalformedJson, UnknownBusiness,
        Transport, Authentication, RegistryUnavailable, KeyUnavailable,
        CryptographicFailure, Gateway5xx,
    ];

    private static readonly ReadOnlyDictionary<string, ErrorCode> _byCode = BuildIndex();

    /// <summary>Every defined error code, in declaration order.</summary>
    public static IReadOnlyList<ErrorCode> All => _all;

    /// <summary>Wire-format code, e.g. <c>"ERR-P-001"</c>.</summary>
    public string Code { get; }

    /// <summary>Tier this code belongs to.</summary>
    public Tier Tier { get; }

    /// <summary>Human-readable description.</summary>
    public string Description { get; }

    private ErrorCode(string code, Tier tier, string description)
    {
        Code = code;
        Tier = tier;
        Description = description;
    }

    /// <summary>
    /// Look up an <see cref="ErrorCode"/> by its wire-format code, or
    /// <see langword="null"/> if no entry matches.
    /// </summary>
    public static ErrorCode? FromWire(string? wireCode)
    {
        if (string.IsNullOrEmpty(wireCode))
        {
            return null;
        }

        return _byCode.TryGetValue(wireCode, out var entry) ? entry : null;
    }

    /// <inheritdoc />
    public override string ToString() => Code;

    private static ReadOnlyDictionary<string, ErrorCode> BuildIndex()
    {
        var dict = new Dictionary<string, ErrorCode>(_all.Count, System.StringComparer.Ordinal);
        foreach (var entry in _all)
        {
            dict[entry.Code] = entry;
        }

        return new ReadOnlyDictionary<string, ErrorCode>(dict);
    }
}
