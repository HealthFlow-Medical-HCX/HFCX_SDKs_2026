"""Exception hierarchy and error-code catalog for the HFCX Python SDK.

Cross-SDK invariant: the wire-format codes here MUST match the Java
SDK's :class:`eg.gov.healthflow.hfcx.sdk.core.exception.ErrorCode`
catalog one-to-one. Drift between the two catalogs is a cross-SDK
conformance bug — the parity audit script in the platform repo
catches it.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from typing import Final


class Tier(Enum):
    """Coarse classification of an error code's tier."""

    PROTOCOL = "P"
    BUSINESS = "B"
    TECHNICAL = "T"


@dataclass(frozen=True, slots=True)
class _ErrorCodeEntry:
    code: str
    tier: Tier
    description: str


class ErrorCode(Enum):
    """Single source of truth for every error code the SDK raises."""

    # ── ERR-P-* — protocol / wire-format violations ─────────────────
    MISSING_HEADER = _ErrorCodeEntry(
        "ERR-P-001", Tier.PROTOCOL, "Required protocol header missing or empty"
    )
    JWE_ALGORITHM_REJECTED = _ErrorCodeEntry(
        "ERR-P-002", Tier.PROTOCOL, "JWE algorithm pair is not the pinned RSA-OAEP-256 / A256GCM"
    )
    RECIPIENT_CODE_MISMATCH = _ErrorCodeEntry(
        "ERR-P-003", Tier.PROTOCOL, "x-hcx-recipient_code does not match this participant"
    )
    BAD_UUID = _ErrorCodeEntry("ERR-P-004", Tier.PROTOCOL, "Header value is not a valid UUID")
    BAD_TIMESTAMP = _ErrorCodeEntry(
        "ERR-P-005", Tier.PROTOCOL, "x-hcx-timestamp is not a valid ISO-8601 instant"
    )
    TIMESTAMP_OUT_OF_RANGE = _ErrorCodeEntry(
        "ERR-P-006", Tier.PROTOCOL, "x-hcx-timestamp is outside the configured tolerance window"
    )
    SENDER_UNKNOWN = _ErrorCodeEntry(
        "ERR-P-007", Tier.PROTOCOL, "Sender participant code is not registered"
    )
    BAD_ENVELOPE = _ErrorCodeEntry("ERR-P-008", Tier.PROTOCOL, "Request body envelope is malformed")
    SIGNATURE_VERIFICATION_FAILED = _ErrorCodeEntry(
        "ERR-P-009", Tier.PROTOCOL, "Detached signature verification failed"
    )

    # ── ERR-B-* — business / FHIR / Egyptian profile violations ─────
    PARTICIPANT_NOT_FOUND = _ErrorCodeEntry(
        "ERR-B-001", Tier.BUSINESS, "Participant code not found in the registry"
    )
    NOT_A_BUNDLE = _ErrorCodeEntry(
        "ERR-B-002", Tier.BUSINESS, "Top-level FHIR resource is not a Bundle"
    )
    BUNDLE_MISSING_TYPE = _ErrorCodeEntry(
        "ERR-B-003", Tier.BUSINESS, "Bundle.type is required by the Egyptian IG"
    )
    PATIENT_MISSING_NATIONAL_ID = _ErrorCodeEntry(
        "ERR-B-004", Tier.BUSINESS, "Patient resource missing the National-ID identifier slice"
    )
    PATIENT_NON_EGYPTIAN = _ErrorCodeEntry(
        "ERR-B-005", Tier.BUSINESS, "Patient.address[0].country must be 'EG'"
    )
    NATIONAL_ID_INVALID = _ErrorCodeEntry(
        "ERR-B-006", Tier.BUSINESS, "Egyptian National ID value fails structural validation"
    )
    PHONE_INVALID = _ErrorCodeEntry(
        "ERR-B-007", Tier.BUSINESS, "Egyptian mobile phone value is not in a recognised format"
    )
    IBAN_INVALID = _ErrorCodeEntry(
        "ERR-B-008", Tier.BUSINESS, "Egyptian IBAN value fails the ISO 13616 mod-97 check"
    )
    BAD_FHIR_JSON = _ErrorCodeEntry("ERR-B-009", Tier.BUSINESS, "FHIR payload is not valid JSON")
    ENVELOPE_MISSING_PAYLOAD = _ErrorCodeEntry(
        "ERR-B-010", Tier.BUSINESS, "Request body envelope is missing the 'payload' field"
    )
    ENVELOPE_MALFORMED_JSON = _ErrorCodeEntry(
        "ERR-B-011", Tier.BUSINESS, "Request body is not valid JSON"
    )
    UNKNOWN_BUSINESS = _ErrorCodeEntry(
        "ERR-B-012", Tier.BUSINESS, "Unspecified business-rule failure"
    )

    # ── ERR-T-* — technical / transport failures ────────────────────
    TRANSPORT = _ErrorCodeEntry("ERR-T-001", Tier.TECHNICAL, "Transport-layer failure")
    AUTHENTICATION = _ErrorCodeEntry(
        "ERR-T-002", Tier.TECHNICAL, "Authentication rejected by the identity provider"
    )
    REGISTRY_UNAVAILABLE = _ErrorCodeEntry(
        "ERR-T-003", Tier.TECHNICAL, "Participant registry is unreachable"
    )
    KEY_UNAVAILABLE = _ErrorCodeEntry(
        "ERR-T-004", Tier.TECHNICAL, "Recipient private key cannot be loaded"
    )
    CRYPTOGRAPHIC_FAILURE = _ErrorCodeEntry(
        "ERR-T-005", Tier.TECHNICAL, "JOSE library reported a cryptographic failure"
    )
    GATEWAY_5XX = _ErrorCodeEntry(
        "ERR-T-006", Tier.TECHNICAL, "HFCX gateway returned a 5xx response after retry exhaustion"
    )

    @property
    def code(self) -> str:
        """Wire-format code, e.g. ``"ERR-P-001"``."""
        entry: _ErrorCodeEntry = self.value
        return entry.code

    @property
    def tier(self) -> Tier:
        """Tier indicating which abstract exception class this code maps to."""
        entry: _ErrorCodeEntry = self.value
        return entry.tier

    @property
    def description(self) -> str:
        """Human-readable description of the failure."""
        entry: _ErrorCodeEntry = self.value
        return entry.description

    @classmethod
    def from_wire(cls, wire_code: str | None) -> ErrorCode | None:
        """Look up a catalog entry by its wire-format code."""
        if wire_code is None:
            return None
        return _BY_WIRE_CODE.get(wire_code)


_BY_WIRE_CODE: Final[dict[str, ErrorCode]] = {entry.code: entry for entry in ErrorCode}


# ─────────────────────────────────────────────────────────────────────
# Exception hierarchy
# ─────────────────────────────────────────────────────────────────────


class HfcxError(Exception):
    """Root exception type for every error surfaced by the HFCX SDK.

    Subtypes follow the platform's three-tier error taxonomy:
    :class:`ProtocolError` for ``ERR-P-xxx``, :class:`BusinessError`
    for ``ERR-B-xxx``, :class:`TechnicalError` for ``ERR-T-xxx``.
    """

    code: str

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code

    @classmethod
    def of(cls, error_code: ErrorCode, message: str) -> HfcxError:
        """Construct the most-specific typed subclass for ``error_code``."""
        return _TYPED_BY_CODE[error_code](message)

    @classmethod
    def from_wire_code(cls, wire_code: str, message: str) -> HfcxError:
        """Look up a wire code in the catalog and return the typed subclass.

        Unknown codes fall back to the bare tier exception based on
        the ``ERR-[PBT]-`` prefix, preserving the platform's reported
        value so callers can still log and triage by it.
        """
        entry = ErrorCode.from_wire(wire_code)
        if entry is not None:
            return cls.of(entry, message)
        if wire_code and len(wire_code) > 5:
            prefix = wire_code[4]
            if prefix == "P":
                return ProtocolError(wire_code, message)
            if prefix == "B":
                return BusinessError(wire_code, message)
            if prefix == "T":
                return TechnicalError(wire_code, message)
        return UnknownBusinessError(f"{message} [unknown wire code: {wire_code!r}]")


class ProtocolError(HfcxError):
    """Tier-2 base for protocol / wire-format violations (``ERR-P-xxx``)."""


class BusinessError(HfcxError):
    """Tier-2 base for business / FHIR / Egyptian-profile violations (``ERR-B-xxx``)."""


class TechnicalError(HfcxError):
    """Tier-2 base for transport / cryptographic / IO failures (``ERR-T-xxx``)."""


# ── Typed subclasses, one per ErrorCode ─────────────────────────────
# Explicit class definitions (rather than dynamic ``type(...)`` calls)
# so mypy --strict can see each constructor's signature. Each subclass
# pins its wire code via ``CODE`` and takes a single ``message`` arg.


class MissingHeaderError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.MISSING_HEADER` (``ERR-P-001``)."""

    CODE: Final[str] = ErrorCode.MISSING_HEADER.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class JweAlgorithmRejectedError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.JWE_ALGORITHM_REJECTED` (``ERR-P-002``)."""

    CODE: Final[str] = ErrorCode.JWE_ALGORITHM_REJECTED.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class RecipientCodeMismatchError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.RECIPIENT_CODE_MISMATCH` (``ERR-P-003``)."""

    CODE: Final[str] = ErrorCode.RECIPIENT_CODE_MISMATCH.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class BadUuidError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.BAD_UUID` (``ERR-P-004``)."""

    CODE: Final[str] = ErrorCode.BAD_UUID.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class BadTimestampError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.BAD_TIMESTAMP` (``ERR-P-005``)."""

    CODE: Final[str] = ErrorCode.BAD_TIMESTAMP.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class TimestampOutOfRangeError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.TIMESTAMP_OUT_OF_RANGE` (``ERR-P-006``)."""

    CODE: Final[str] = ErrorCode.TIMESTAMP_OUT_OF_RANGE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class SenderUnknownError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.SENDER_UNKNOWN` (``ERR-P-007``)."""

    CODE: Final[str] = ErrorCode.SENDER_UNKNOWN.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class BadEnvelopeError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.BAD_ENVELOPE` (``ERR-P-008``)."""

    CODE: Final[str] = ErrorCode.BAD_ENVELOPE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class SignatureVerificationFailedError(ProtocolError):
    """Typed exception for :attr:`ErrorCode.SIGNATURE_VERIFICATION_FAILED` (``ERR-P-009``)."""

    CODE: Final[str] = ErrorCode.SIGNATURE_VERIFICATION_FAILED.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class ParticipantNotFoundError(BusinessError):
    """Typed exception for :attr:`ErrorCode.PARTICIPANT_NOT_FOUND` (``ERR-B-001``)."""

    CODE: Final[str] = ErrorCode.PARTICIPANT_NOT_FOUND.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class NotABundleError(BusinessError):
    """Typed exception for :attr:`ErrorCode.NOT_A_BUNDLE` (``ERR-B-002``)."""

    CODE: Final[str] = ErrorCode.NOT_A_BUNDLE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class BundleMissingTypeError(BusinessError):
    """Typed exception for :attr:`ErrorCode.BUNDLE_MISSING_TYPE` (``ERR-B-003``)."""

    CODE: Final[str] = ErrorCode.BUNDLE_MISSING_TYPE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class PatientMissingNationalIdError(BusinessError):
    """Typed exception for :attr:`ErrorCode.PATIENT_MISSING_NATIONAL_ID` (``ERR-B-004``)."""

    CODE: Final[str] = ErrorCode.PATIENT_MISSING_NATIONAL_ID.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class PatientNonEgyptianError(BusinessError):
    """Typed exception for :attr:`ErrorCode.PATIENT_NON_EGYPTIAN` (``ERR-B-005``)."""

    CODE: Final[str] = ErrorCode.PATIENT_NON_EGYPTIAN.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class NationalIdInvalidError(BusinessError):
    """Typed exception for :attr:`ErrorCode.NATIONAL_ID_INVALID` (``ERR-B-006``)."""

    CODE: Final[str] = ErrorCode.NATIONAL_ID_INVALID.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class PhoneInvalidError(BusinessError):
    """Typed exception for :attr:`ErrorCode.PHONE_INVALID` (``ERR-B-007``)."""

    CODE: Final[str] = ErrorCode.PHONE_INVALID.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class IbanInvalidError(BusinessError):
    """Typed exception for :attr:`ErrorCode.IBAN_INVALID` (``ERR-B-008``)."""

    CODE: Final[str] = ErrorCode.IBAN_INVALID.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class BadFhirJsonError(BusinessError):
    """Typed exception for :attr:`ErrorCode.BAD_FHIR_JSON` (``ERR-B-009``)."""

    CODE: Final[str] = ErrorCode.BAD_FHIR_JSON.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class EnvelopeMissingPayloadError(BusinessError):
    """Typed exception for :attr:`ErrorCode.ENVELOPE_MISSING_PAYLOAD` (``ERR-B-010``)."""

    CODE: Final[str] = ErrorCode.ENVELOPE_MISSING_PAYLOAD.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class EnvelopeMalformedJsonError(BusinessError):
    """Typed exception for :attr:`ErrorCode.ENVELOPE_MALFORMED_JSON` (``ERR-B-011``)."""

    CODE: Final[str] = ErrorCode.ENVELOPE_MALFORMED_JSON.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class UnknownBusinessError(BusinessError):
    """Typed exception for :attr:`ErrorCode.UNKNOWN_BUSINESS` (``ERR-B-012``)."""

    CODE: Final[str] = ErrorCode.UNKNOWN_BUSINESS.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class TransportError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.TRANSPORT` (``ERR-T-001``)."""

    CODE: Final[str] = ErrorCode.TRANSPORT.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class AuthenticationError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.AUTHENTICATION` (``ERR-T-002``)."""

    CODE: Final[str] = ErrorCode.AUTHENTICATION.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class RegistryUnavailableError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.REGISTRY_UNAVAILABLE` (``ERR-T-003``)."""

    CODE: Final[str] = ErrorCode.REGISTRY_UNAVAILABLE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class KeyUnavailableError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.KEY_UNAVAILABLE` (``ERR-T-004``)."""

    CODE: Final[str] = ErrorCode.KEY_UNAVAILABLE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class CryptographicFailureError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.CRYPTOGRAPHIC_FAILURE` (``ERR-T-005``)."""

    CODE: Final[str] = ErrorCode.CRYPTOGRAPHIC_FAILURE.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


class Gateway5xxError(TechnicalError):
    """Typed exception for :attr:`ErrorCode.GATEWAY_5XX` (``ERR-T-006``)."""

    CODE: Final[str] = ErrorCode.GATEWAY_5XX.code

    def __init__(self, message: str) -> None:
        super().__init__(self.CODE, message)


_TYPED_BY_CODE: Final[dict[ErrorCode, Callable[[str], HfcxError]]] = {
    ErrorCode.MISSING_HEADER: MissingHeaderError,
    ErrorCode.JWE_ALGORITHM_REJECTED: JweAlgorithmRejectedError,
    ErrorCode.RECIPIENT_CODE_MISMATCH: RecipientCodeMismatchError,
    ErrorCode.BAD_UUID: BadUuidError,
    ErrorCode.BAD_TIMESTAMP: BadTimestampError,
    ErrorCode.TIMESTAMP_OUT_OF_RANGE: TimestampOutOfRangeError,
    ErrorCode.SENDER_UNKNOWN: SenderUnknownError,
    ErrorCode.BAD_ENVELOPE: BadEnvelopeError,
    ErrorCode.SIGNATURE_VERIFICATION_FAILED: SignatureVerificationFailedError,
    ErrorCode.PARTICIPANT_NOT_FOUND: ParticipantNotFoundError,
    ErrorCode.NOT_A_BUNDLE: NotABundleError,
    ErrorCode.BUNDLE_MISSING_TYPE: BundleMissingTypeError,
    ErrorCode.PATIENT_MISSING_NATIONAL_ID: PatientMissingNationalIdError,
    ErrorCode.PATIENT_NON_EGYPTIAN: PatientNonEgyptianError,
    ErrorCode.NATIONAL_ID_INVALID: NationalIdInvalidError,
    ErrorCode.PHONE_INVALID: PhoneInvalidError,
    ErrorCode.IBAN_INVALID: IbanInvalidError,
    ErrorCode.BAD_FHIR_JSON: BadFhirJsonError,
    ErrorCode.ENVELOPE_MISSING_PAYLOAD: EnvelopeMissingPayloadError,
    ErrorCode.ENVELOPE_MALFORMED_JSON: EnvelopeMalformedJsonError,
    ErrorCode.UNKNOWN_BUSINESS: UnknownBusinessError,
    ErrorCode.TRANSPORT: TransportError,
    ErrorCode.AUTHENTICATION: AuthenticationError,
    ErrorCode.REGISTRY_UNAVAILABLE: RegistryUnavailableError,
    ErrorCode.KEY_UNAVAILABLE: KeyUnavailableError,
    ErrorCode.CRYPTOGRAPHIC_FAILURE: CryptographicFailureError,
    ErrorCode.GATEWAY_5XX: Gateway5xxError,
}


__all__ = [
    "AuthenticationError",
    "BadEnvelopeError",
    "BadFhirJsonError",
    "BadTimestampError",
    "BadUuidError",
    "BundleMissingTypeError",
    "BusinessError",
    "CryptographicFailureError",
    "EnvelopeMalformedJsonError",
    "EnvelopeMissingPayloadError",
    "ErrorCode",
    "Gateway5xxError",
    "HfcxError",
    "IbanInvalidError",
    "JweAlgorithmRejectedError",
    "KeyUnavailableError",
    "MissingHeaderError",
    "NationalIdInvalidError",
    "NotABundleError",
    "ParticipantNotFoundError",
    "PatientMissingNationalIdError",
    "PatientNonEgyptianError",
    "PhoneInvalidError",
    "ProtocolError",
    "RecipientCodeMismatchError",
    "RegistryUnavailableError",
    "SenderUnknownError",
    "SignatureVerificationFailedError",
    "TechnicalError",
    "Tier",
    "TimestampOutOfRangeError",
    "TransportError",
    "UnknownBusinessError",
]
