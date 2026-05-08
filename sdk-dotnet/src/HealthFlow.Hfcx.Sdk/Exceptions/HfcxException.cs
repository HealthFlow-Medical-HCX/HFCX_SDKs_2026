// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;

namespace HealthFlow.Hfcx.Sdk.Exceptions;

/// <summary>
/// Root exception type for every error surfaced by the HFCX SDK. Subtypes
/// follow the platform's three-tier error taxonomy:
/// <see cref="ProtocolException"/> for <c>ERR-P-*</c>,
/// <see cref="BusinessException"/> for <c>ERR-B-*</c>, and
/// <see cref="TechnicalException"/> for <c>ERR-T-*</c>.
/// </summary>
public class HfcxException : Exception
{
    /// <summary>Wire-format code carried with this exception, e.g. <c>"ERR-P-001"</c>.</summary>
    public string Code { get; }

    /// <summary>Construct an exception with an explicit wire-format code and message.</summary>
    public HfcxException(string code, string message)
        : base(message)
    {
        Code = code;
    }

    /// <summary>Construct an exception with an explicit wire code, message, and inner exception.</summary>
    public HfcxException(string code, string message, Exception? innerException)
        : base(message, innerException)
    {
        Code = code;
    }

    /// <summary>
    /// Construct the most-specific typed subclass for <paramref name="errorCode"/>.
    /// </summary>
    public static HfcxException Of(ErrorCode errorCode, string message)
    {
        ArgumentNullException.ThrowIfNull(errorCode);

        return errorCode.Code switch
        {
            "ERR-P-001" => new MissingHeaderException(message),
            "ERR-P-002" => new JweAlgorithmRejectedException(message),
            "ERR-P-003" => new RecipientCodeMismatchException(message),
            "ERR-P-004" => new BadUuidException(message),
            "ERR-P-005" => new BadTimestampException(message),
            "ERR-P-006" => new TimestampOutOfRangeException(message),
            "ERR-P-007" => new SenderUnknownException(message),
            "ERR-P-008" => new BadEnvelopeException(message),
            "ERR-P-009" => new SignatureVerificationFailedException(message),
            "ERR-B-001" => new ParticipantNotFoundException(message),
            "ERR-B-002" => new NotABundleException(message),
            "ERR-B-003" => new BundleMissingTypeException(message),
            "ERR-B-004" => new PatientMissingNationalIdException(message),
            "ERR-B-005" => new PatientNonEgyptianException(message),
            "ERR-B-006" => new NationalIdInvalidException(message),
            "ERR-B-007" => new PhoneInvalidException(message),
            "ERR-B-008" => new IbanInvalidException(message),
            "ERR-B-009" => new BadFhirJsonException(message),
            "ERR-B-010" => new EnvelopeMissingPayloadException(message),
            "ERR-B-011" => new EnvelopeMalformedJsonException(message),
            "ERR-B-012" => new UnknownBusinessException(message),
            "ERR-T-001" => new TransportException(message),
            "ERR-T-002" => new AuthenticationException(message),
            "ERR-T-003" => new RegistryUnavailableException(message),
            "ERR-T-004" => new KeyUnavailableException(message),
            "ERR-T-005" => new CryptographicFailureException(message),
            "ERR-T-006" => new Gateway5xxException(message),
            _ => throw new ArgumentException($"Unknown error code {errorCode.Code}", nameof(errorCode)),
        };
    }

    /// <summary>
    /// Look up <paramref name="wireCode"/> in the catalog and return the
    /// most-specific typed subclass. Falls back to the bare tier exception
    /// based on the <c>ERR-[PBT]-</c> prefix when the code is unknown.
    /// </summary>
    public static HfcxException FromWireCode(string wireCode, string message)
    {
        var entry = ErrorCode.FromWire(wireCode);
        if (entry is not null)
        {
            return Of(entry, message);
        }

        if (!string.IsNullOrEmpty(wireCode) && wireCode.Length > 5)
        {
            var prefix = wireCode[4];
            if (prefix == 'P')
            {
                return new ProtocolException(wireCode, message);
            }

            if (prefix == 'B')
            {
                return new BusinessException(wireCode, message);
            }

            if (prefix == 'T')
            {
                return new TechnicalException(wireCode, message);
            }
        }

        return new UnknownBusinessException($"{message} [unknown wire code: '{wireCode}']");
    }
}

/// <summary>Tier-2 base for protocol / wire-format violations (<c>ERR-P-*</c>).</summary>
public class ProtocolException : HfcxException
{
    public ProtocolException(string code, string message)
        : base(code, message)
    {
    }
}

/// <summary>Tier-2 base for business / FHIR / Egyptian-profile violations (<c>ERR-B-*</c>).</summary>
public class BusinessException : HfcxException
{
    public BusinessException(string code, string message)
        : base(code, message)
    {
    }
}

/// <summary>Tier-2 base for transport / cryptographic / IO failures (<c>ERR-T-*</c>).</summary>
public class TechnicalException : HfcxException
{
    public TechnicalException(string code, string message)
        : base(code, message)
    {
    }
}
