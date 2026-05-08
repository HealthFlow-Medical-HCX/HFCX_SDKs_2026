"""Cross-SDK invariant tests for the ErrorCode catalog.

Sister to the Java SDK's ``ErrorCodeCatalogTest``. Drift between
the two catalogs is a cross-SDK conformance bug.
"""

from __future__ import annotations

import re
from collections.abc import Callable

import pytest

from hfcx_sdk.exceptions import (
    AuthenticationError,
    BadEnvelopeError,
    BadFhirJsonError,
    BadTimestampError,
    BadUuidError,
    BundleMissingTypeError,
    BusinessError,
    CryptographicFailureError,
    EnvelopeMalformedJsonError,
    EnvelopeMissingPayloadError,
    ErrorCode,
    Gateway5xxError,
    HfcxError,
    IbanInvalidError,
    JweAlgorithmRejectedError,
    KeyUnavailableError,
    MissingHeaderError,
    NationalIdInvalidError,
    NotABundleError,
    ParticipantNotFoundError,
    PatientMissingNationalIdError,
    PatientNonEgyptianError,
    PhoneInvalidError,
    ProtocolError,
    RecipientCodeMismatchError,
    RegistryUnavailableError,
    SenderUnknownError,
    SignatureVerificationFailedError,
    TechnicalError,
    Tier,
    TimestampOutOfRangeError,
    TransportError,
    UnknownBusinessError,
)

WIRE_FORMAT = re.compile(r"^ERR-[PBT]-\d{3}$")

ALL_TYPED = [
    MissingHeaderError,
    JweAlgorithmRejectedError,
    RecipientCodeMismatchError,
    BadUuidError,
    BadTimestampError,
    TimestampOutOfRangeError,
    SenderUnknownError,
    BadEnvelopeError,
    SignatureVerificationFailedError,
    ParticipantNotFoundError,
    NotABundleError,
    BundleMissingTypeError,
    PatientMissingNationalIdError,
    PatientNonEgyptianError,
    NationalIdInvalidError,
    PhoneInvalidError,
    IbanInvalidError,
    BadFhirJsonError,
    EnvelopeMissingPayloadError,
    EnvelopeMalformedJsonError,
    UnknownBusinessError,
    TransportError,
    AuthenticationError,
    RegistryUnavailableError,
    KeyUnavailableError,
    CryptographicFailureError,
    Gateway5xxError,
]


def test_every_entry_has_a_unique_wire_code() -> None:
    codes = {entry.code for entry in ErrorCode}
    assert len(codes) == len(list(ErrorCode))


def test_every_wire_code_matches_canonical_format() -> None:
    for entry in ErrorCode:
        assert WIRE_FORMAT.match(entry.code), f"{entry} has wire format {entry.code!r}"


def test_wire_code_prefix_matches_declared_tier() -> None:
    for entry in ErrorCode:
        prefix = entry.code[4]
        expected = {"P": Tier.PROTOCOL, "B": Tier.BUSINESS, "T": Tier.TECHNICAL}[prefix]
        assert entry.tier == expected, f"{entry} prefix={prefix} tier={entry.tier}"


def test_every_entry_has_a_non_empty_description() -> None:
    for entry in ErrorCode:
        assert entry.description and entry.description.strip()


def test_from_wire_round_trips() -> None:
    for entry in ErrorCode:
        assert ErrorCode.from_wire(entry.code) is entry


def test_from_wire_returns_none_for_unknown() -> None:
    assert ErrorCode.from_wire(None) is None
    assert ErrorCode.from_wire("") is None
    assert ErrorCode.from_wire("ERR-X-999") is None
    assert ErrorCode.from_wire("not-a-code") is None


def test_factory_produces_correct_tier() -> None:
    assert isinstance(HfcxError.of(ErrorCode.MISSING_HEADER, "x"), ProtocolError)
    assert isinstance(HfcxError.of(ErrorCode.NATIONAL_ID_INVALID, "x"), BusinessError)
    assert isinstance(HfcxError.of(ErrorCode.TRANSPORT, "x"), TechnicalError)


def test_factory_preserves_wire_code() -> None:
    for entry in ErrorCode:
        ex = HfcxError.of(entry, "msg")
        assert ex.code == entry.code


def test_per_tier_counts_are_pinned() -> None:
    # Identical to the Java SDK's pinned counts. Cross-SDK invariant.
    protocols = sum(1 for e in ErrorCode if e.tier == Tier.PROTOCOL)
    business = sum(1 for e in ErrorCode if e.tier == Tier.BUSINESS)
    technical = sum(1 for e in ErrorCode if e.tier == Tier.TECHNICAL)
    assert protocols == 9
    assert business == 12
    assert technical == 6


def test_every_catalog_entry_has_a_matching_typed_subclass() -> None:
    catalog_codes = {entry.code for entry in ErrorCode}
    typed_codes = {cls.CODE for cls in ALL_TYPED}  # type: ignore[attr-defined]
    assert catalog_codes == typed_codes
    assert len(ALL_TYPED) == len(list(ErrorCode))


def test_from_wire_code_returns_typed_subclass() -> None:
    ex = HfcxError.from_wire_code("ERR-B-006", "bad id")
    assert isinstance(ex, NationalIdInvalidError)
    assert ex.code == "ERR-B-006"


def test_from_wire_code_falls_back_for_unknown_codes() -> None:
    # Unknown code with a known prefix → bare tier exception, code preserved.
    ex_t = HfcxError.from_wire_code("ERR-T-099", "future-code")
    assert isinstance(ex_t, TechnicalError)
    assert ex_t.code == "ERR-T-099"

    # Unknown prefix → unknown-business slot.
    ex_x = HfcxError.from_wire_code("ERR-X-001", "alien code")
    assert isinstance(ex_x, UnknownBusinessError)


def test_authentication_error_is_a_technical_error() -> None:
    ex = AuthenticationError("bad creds")
    assert isinstance(ex, TechnicalError)
    assert ex.code == "ERR-T-002"


@pytest.mark.parametrize("typed_cls", ALL_TYPED)
def test_typed_subclass_pins_its_code(typed_cls: Callable[[str], HfcxError]) -> None:
    ex = typed_cls("msg")
    assert ex.code == typed_cls.CODE  # type: ignore[attr-defined]
    assert "msg" in str(ex)
