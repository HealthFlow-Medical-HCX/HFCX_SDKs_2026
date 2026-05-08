"""Dedicated unit tests for :class:`EgyptianBundleValidator`.

Pinned alongside the Java SDK's ``EgyptianBundleValidator`` —
identical Bundle JSON must produce the same accept / reject decision
in both SDKs.
"""

from __future__ import annotations

import json
from typing import Any

import pytest

from hfcx_sdk import EgyptianBundleValidator
from hfcx_sdk.exceptions import (
    IbanInvalidError,
    NationalIdInvalidError,
    PhoneInvalidError,
)
from hfcx_sdk.recipient import NATIONAL_ID_SYSTEM


def _bundle(*resources: dict[str, Any]) -> str:
    return json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [{"resource": r} for r in resources],
        }
    )


def _patient(
    *,
    nid: str | None = "29504150112345",
    phone: str | None = None,
) -> dict[str, Any]:
    identifiers: list[dict[str, str]] = []
    if nid is not None:
        identifiers.append({"system": NATIONAL_ID_SYSTEM, "value": nid})
    resource: dict[str, Any] = {
        "resourceType": "Patient",
        "identifier": identifiers,
        "address": [{"country": "EG"}],
    }
    if phone is not None:
        resource["telecom"] = [{"system": "phone", "value": phone}]
    return resource


def _organisation(*identifiers: dict[str, str]) -> dict[str, Any]:
    return {"resourceType": "Organization", "name": "PayerCo", "identifier": list(identifiers)}


# ── happy paths ─────────────────────────────────────────────────────


def test_valid_patient_passes() -> None:
    EgyptianBundleValidator().validate(_bundle(_patient()))


def test_valid_patient_with_phone_passes() -> None:
    EgyptianBundleValidator().validate(_bundle(_patient(phone="+201012345678")))


def test_valid_organisation_iban_passes() -> None:
    iban = {"system": "https://example.org/iban", "value": "EG380019000500000000263180002"}
    EgyptianBundleValidator().validate(_bundle(_organisation(iban)))


def test_empty_payload_returns_silently() -> None:
    EgyptianBundleValidator().validate("")
    EgyptianBundleValidator().validate("   ")


def test_malformed_json_returns_silently() -> None:
    # FhirValidator runs first; if we somehow get malformed JSON here,
    # fail-secure no-op (matches Java).
    EgyptianBundleValidator().validate("{not valid")


def test_non_object_root_returns_silently() -> None:
    EgyptianBundleValidator().validate('"hello"')
    EgyptianBundleValidator().validate("[]")


def test_no_entry_array_returns_silently() -> None:
    EgyptianBundleValidator().validate('{"resourceType":"Bundle","type":"collection"}')


def test_unknown_resource_types_skipped() -> None:
    practitioner = {"resourceType": "Practitioner", "id": "p1"}
    EgyptianBundleValidator().validate(_bundle(practitioner))


# ── National-ID failures ────────────────────────────────────────────


def test_invalid_national_id_value_raises() -> None:
    with pytest.raises(NationalIdInvalidError) as excinfo:
        EgyptianBundleValidator().validate(_bundle(_patient(nid="not-a-real-nid")))
    assert "not-a-real-nid" in str(excinfo.value)


def test_national_id_with_wrong_length_raises() -> None:
    with pytest.raises(NationalIdInvalidError):
        EgyptianBundleValidator().validate(_bundle(_patient(nid="1234")))


def test_national_id_with_unknown_governorate_raises() -> None:
    # gov code 99 is not a valid governorate prefix.
    with pytest.raises(NationalIdInvalidError):
        EgyptianBundleValidator().validate(_bundle(_patient(nid="29504150999991")))


def test_national_id_with_impossible_date_raises() -> None:
    with pytest.raises(NationalIdInvalidError):
        EgyptianBundleValidator().validate(_bundle(_patient(nid="29513320112345")))


def test_patient_with_no_national_id_identifier_passes_at_this_layer() -> None:
    # Missing-NID is FhirValidator's job; if FhirValidator is disabled
    # but EgyptianBundleValidator is enabled, the bundle walker should
    # not synthesise an error for an absent slice.
    EgyptianBundleValidator().validate(_bundle(_patient(nid=None)))


def test_other_identifier_system_ignored() -> None:
    bundle = _bundle(
        {
            "resourceType": "Patient",
            "identifier": [{"system": "http://example.org/passport", "value": "anything"}],
            "address": [{"country": "EG"}],
        }
    )
    EgyptianBundleValidator().validate(bundle)


# ── Phone failures ──────────────────────────────────────────────────


def test_invalid_phone_value_raises() -> None:
    with pytest.raises(PhoneInvalidError) as excinfo:
        EgyptianBundleValidator().validate(_bundle(_patient(phone="01312345678")))
    assert "01312345678" in str(excinfo.value)


def test_phone_with_wrong_country_code_raises() -> None:
    with pytest.raises(PhoneInvalidError):
        EgyptianBundleValidator().validate(_bundle(_patient(phone="+30101234567")))


def test_telecom_other_systems_ignored() -> None:
    bundle = _bundle(
        {
            "resourceType": "Patient",
            "identifier": [{"system": NATIONAL_ID_SYSTEM, "value": "29504150112345"}],
            "address": [{"country": "EG"}],
            "telecom": [
                {"system": "email", "value": "patient@example.com"},
                {"system": "fax", "value": "0211111111"},
            ],
        }
    )
    EgyptianBundleValidator().validate(bundle)


# ── IBAN failures ───────────────────────────────────────────────────


def test_invalid_iban_value_raises() -> None:
    iban = {"system": "https://example.org/iban", "value": "EG380019000500000000263180003"}
    with pytest.raises(IbanInvalidError) as excinfo:
        EgyptianBundleValidator().validate(_bundle(_organisation(iban)))
    assert "EG38" in str(excinfo.value)


def test_iban_with_wrong_country_raises() -> None:
    iban = {"system": "https://example.org/iban", "value": "FR380019000500000000263180002"}
    with pytest.raises(IbanInvalidError):
        EgyptianBundleValidator().validate(_bundle(_organisation(iban)))


def test_organisation_identifier_without_iban_system_ignored() -> None:
    # System URI without the substring "iban" → skipped (matches Java's
    # case-sensitive substring match on "iban").
    other = {"system": "http://example.org/tax-id", "value": "garbage-value"}
    EgyptianBundleValidator().validate(_bundle(_organisation(other)))


def test_organisation_without_identifiers_ignored() -> None:
    EgyptianBundleValidator().validate(_bundle({"resourceType": "Organization", "name": "PayerCo"}))


# ── Multi-resource walks ────────────────────────────────────────────


def test_first_invalid_resource_short_circuits() -> None:
    good_org_iban = {"system": "https://example.org/iban", "value": "EG380019000500000000263180002"}
    bad_phone_patient = _patient(phone="01312345678")
    with pytest.raises(PhoneInvalidError):
        EgyptianBundleValidator().validate(_bundle(_organisation(good_org_iban), bad_phone_patient))


def test_multiple_organisations_each_iban_checked() -> None:
    good_iban = {"system": "https://example.org/iban", "value": "EG380019000500000000263180002"}
    bad_iban = {"system": "https://example.org/iban", "value": "EG380019000500000000263180003"}
    with pytest.raises(IbanInvalidError):
        EgyptianBundleValidator().validate(
            _bundle(_organisation(good_iban), _organisation(bad_iban))
        )
