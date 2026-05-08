// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

// One typed exception subclass per <see cref="ErrorCode"/> entry. Grouped in
// a single file rather than the Java-SDK-style one-per-file layout —
// individual files are noisy in C# where exception classes are typically
// 5-line records. The cross-SDK invariant is preserved: each subclass pins
// the same wire-format code as its Java counterpart.

namespace HealthFlow.Hfcx.Sdk.Exceptions;

// ── Protocol (ERR-P-*) ──────────────────────────────────────────────────

public sealed class MissingHeaderException : ProtocolException
{
    public const string CodeValue = "ERR-P-001";
    public MissingHeaderException(string message) : base(CodeValue, message) { }
}

public sealed class JweAlgorithmRejectedException : ProtocolException
{
    public const string CodeValue = "ERR-P-002";
    public JweAlgorithmRejectedException(string message) : base(CodeValue, message) { }
}

public sealed class RecipientCodeMismatchException : ProtocolException
{
    public const string CodeValue = "ERR-P-003";
    public RecipientCodeMismatchException(string message) : base(CodeValue, message) { }
}

public sealed class BadUuidException : ProtocolException
{
    public const string CodeValue = "ERR-P-004";
    public BadUuidException(string message) : base(CodeValue, message) { }
}

public sealed class BadTimestampException : ProtocolException
{
    public const string CodeValue = "ERR-P-005";
    public BadTimestampException(string message) : base(CodeValue, message) { }
}

public sealed class TimestampOutOfRangeException : ProtocolException
{
    public const string CodeValue = "ERR-P-006";
    public TimestampOutOfRangeException(string message) : base(CodeValue, message) { }
}

public sealed class SenderUnknownException : ProtocolException
{
    public const string CodeValue = "ERR-P-007";
    public SenderUnknownException(string message) : base(CodeValue, message) { }
}

public sealed class BadEnvelopeException : ProtocolException
{
    public const string CodeValue = "ERR-P-008";
    public BadEnvelopeException(string message) : base(CodeValue, message) { }
}

public sealed class SignatureVerificationFailedException : ProtocolException
{
    public const string CodeValue = "ERR-P-009";
    public SignatureVerificationFailedException(string message) : base(CodeValue, message) { }
}

// ── Business (ERR-B-*) ──────────────────────────────────────────────────

public sealed class ParticipantNotFoundException : BusinessException
{
    public const string CodeValue = "ERR-B-001";
    public ParticipantNotFoundException(string message) : base(CodeValue, message) { }
}

public sealed class NotABundleException : BusinessException
{
    public const string CodeValue = "ERR-B-002";
    public NotABundleException(string message) : base(CodeValue, message) { }
}

public sealed class BundleMissingTypeException : BusinessException
{
    public const string CodeValue = "ERR-B-003";
    public BundleMissingTypeException(string message) : base(CodeValue, message) { }
}

public sealed class PatientMissingNationalIdException : BusinessException
{
    public const string CodeValue = "ERR-B-004";
    public PatientMissingNationalIdException(string message) : base(CodeValue, message) { }
}

public sealed class PatientNonEgyptianException : BusinessException
{
    public const string CodeValue = "ERR-B-005";
    public PatientNonEgyptianException(string message) : base(CodeValue, message) { }
}

public sealed class NationalIdInvalidException : BusinessException
{
    public const string CodeValue = "ERR-B-006";
    public NationalIdInvalidException(string message) : base(CodeValue, message) { }
}

public sealed class PhoneInvalidException : BusinessException
{
    public const string CodeValue = "ERR-B-007";
    public PhoneInvalidException(string message) : base(CodeValue, message) { }
}

public sealed class IbanInvalidException : BusinessException
{
    public const string CodeValue = "ERR-B-008";
    public IbanInvalidException(string message) : base(CodeValue, message) { }
}

public sealed class BadFhirJsonException : BusinessException
{
    public const string CodeValue = "ERR-B-009";
    public BadFhirJsonException(string message) : base(CodeValue, message) { }
}

public sealed class EnvelopeMissingPayloadException : BusinessException
{
    public const string CodeValue = "ERR-B-010";
    public EnvelopeMissingPayloadException(string message) : base(CodeValue, message) { }
}

public sealed class EnvelopeMalformedJsonException : BusinessException
{
    public const string CodeValue = "ERR-B-011";
    public EnvelopeMalformedJsonException(string message) : base(CodeValue, message) { }
}

public sealed class UnknownBusinessException : BusinessException
{
    public const string CodeValue = "ERR-B-012";
    public UnknownBusinessException(string message) : base(CodeValue, message) { }
}

// ── Technical (ERR-T-*) ─────────────────────────────────────────────────

public sealed class TransportException : TechnicalException
{
    public const string CodeValue = "ERR-T-001";
    public TransportException(string message) : base(CodeValue, message) { }
}

public sealed class AuthenticationException : TechnicalException
{
    public const string CodeValue = "ERR-T-002";
    public AuthenticationException(string message) : base(CodeValue, message) { }
}

public sealed class RegistryUnavailableException : TechnicalException
{
    public const string CodeValue = "ERR-T-003";
    public RegistryUnavailableException(string message) : base(CodeValue, message) { }
}

public sealed class KeyUnavailableException : TechnicalException
{
    public const string CodeValue = "ERR-T-004";
    public KeyUnavailableException(string message) : base(CodeValue, message) { }
}

public sealed class CryptographicFailureException : TechnicalException
{
    public const string CodeValue = "ERR-T-005";
    public CryptographicFailureException(string message) : base(CodeValue, message) { }
}

public sealed class Gateway5xxException : TechnicalException
{
    public const string CodeValue = "ERR-T-006";
    public Gateway5xxException(string message) : base(CodeValue, message) { }
}
