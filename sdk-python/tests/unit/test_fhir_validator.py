"""Dedicated unit tests for :class:`FhirValidator`.

These exercise the validator directly (without the full
RecipientHandler pipeline) so each error path is pinned independently.
Cross-SDK invariant: same Bundle JSON → same accept / reject decision
as the Java SDK's ``FhirValidator``.
"""

from __future__ import annotations

import json
from collections.abc import Mapping
from typing import Any

import pytest

from hfcx_sdk import FhirValidator
from hfcx_sdk.exceptions import (
    BadFhirJsonError,
    BundleMissingTypeError,
    NotABundleError,
    PatientMissingNationalIdError,
    PatientNonEgyptianError,
)
from hfcx_sdk.recipient import NATIONAL_ID_SYSTEM


def _patient(
    *,
    nid: str | None = "29504150112345",
    country: str | None = "EG",
    extra_identifier: Mapping[str, str] | None = None,
    skip_address: bool = False,
) -> dict[str, Any]:
    identifiers: list[dict[str, str]] = []
    if nid is not None:
        identifiers.append({"system": NATIONAL_ID_SYSTEM, "value": nid})
    if extra_identifier is not None:
        identifiers.append(dict(extra_identifier))
    resource: dict[str, Any] = {"resourceType": "Patient", "identifier": identifiers}
    if not skip_address:
        resource["address"] = [{"country": country}] if country is not None else [{}]
    return resource


def _bundle(*resources: Mapping[str, Any], bundle_type: str | None = "collection") -> str:
    body: dict[str, Any] = {"resourceType": "Bundle"}
    if bundle_type is not None:
        body["type"] = bundle_type
    body["entry"] = [{"resource": dict(r)} for r in resources]
    return json.dumps(body)


def test_valid_bundle_with_egyptian_patient_passes() -> None:
    FhirValidator().validate(_bundle(_patient()))


def test_valid_bundle_without_patient_passes() -> None:
    # Bundle with only Organization should pass — no Patient slice to check.
    FhirValidator().validate(_bundle({"resourceType": "Organization", "name": "PayerCo"}))


def test_empty_bundle_entry_array_passes() -> None:
    FhirValidator().validate('{"resourceType":"Bundle","type":"collection","entry":[]}')


def test_missing_entry_key_passes() -> None:
    # No entry array at all is a legal (if empty) Bundle.
    FhirValidator().validate('{"resourceType":"Bundle","type":"collection"}')


@pytest.mark.parametrize("bad", ["", "   ", "\t\n"])
def test_blank_payload_raises_bad_fhir_json(bad: str) -> None:
    with pytest.raises(BadFhirJsonError):
        FhirValidator().validate(bad)


def test_malformed_json_raises_bad_fhir_json() -> None:
    with pytest.raises(BadFhirJsonError):
        FhirValidator().validate("{not: valid json}")


def test_top_level_array_raises_not_a_bundle() -> None:
    with pytest.raises(NotABundleError):
        FhirValidator().validate('[{"resourceType":"Bundle"}]')


def test_top_level_string_raises_not_a_bundle() -> None:
    with pytest.raises(NotABundleError):
        FhirValidator().validate('"Bundle"')


def test_wrong_resource_type_raises_not_a_bundle() -> None:
    with pytest.raises(NotABundleError) as excinfo:
        FhirValidator().validate('{"resourceType":"Patient","type":"collection"}')
    assert "Patient" in str(excinfo.value)


def test_missing_resource_type_raises_not_a_bundle() -> None:
    with pytest.raises(NotABundleError):
        FhirValidator().validate('{"type":"collection"}')


def test_missing_bundle_type_raises_bundle_missing_type() -> None:
    with pytest.raises(BundleMissingTypeError):
        FhirValidator().validate('{"resourceType":"Bundle"}')


def test_empty_bundle_type_raises_bundle_missing_type() -> None:
    with pytest.raises(BundleMissingTypeError):
        FhirValidator().validate('{"resourceType":"Bundle","type":""}')


def test_patient_with_no_identifier_array_raises() -> None:
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [{"resource": {"resourceType": "Patient", "address": [{"country": "EG"}]}}],
        }
    )
    with pytest.raises(PatientMissingNationalIdError):
        FhirValidator().validate(bundle)


def test_patient_with_only_other_identifier_system_raises() -> None:
    other = {"system": "http://example.org/passport", "value": "P12345"}
    with pytest.raises(PatientMissingNationalIdError):
        FhirValidator().validate(_bundle(_patient(nid=None, extra_identifier=other)))


def test_patient_with_empty_identifier_array_raises() -> None:
    with pytest.raises(PatientMissingNationalIdError):
        FhirValidator().validate(_bundle(_patient(nid=None)))


def test_patient_without_address_raises_non_egyptian() -> None:
    with pytest.raises(PatientNonEgyptianError):
        FhirValidator().validate(_bundle(_patient(skip_address=True)))


def test_patient_with_empty_address_array_raises_non_egyptian() -> None:
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [{"system": NATIONAL_ID_SYSTEM, "value": "29504150112345"}],
                        "address": [],
                    }
                }
            ],
        }
    )
    with pytest.raises(PatientNonEgyptianError):
        FhirValidator().validate(bundle)


def test_patient_with_non_eg_country_raises_non_egyptian() -> None:
    with pytest.raises(PatientNonEgyptianError) as excinfo:
        FhirValidator().validate(_bundle(_patient(country="US")))
    assert "US" in str(excinfo.value)


def test_patient_with_missing_country_raises_non_egyptian() -> None:
    with pytest.raises(PatientNonEgyptianError):
        FhirValidator().validate(_bundle(_patient(country=None)))


def test_first_address_must_be_egyptian_subsequent_addresses_ignored() -> None:
    # The Egyptian IG pins address[0] specifically; later entries do not
    # rescue a non-Egyptian first entry.
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [
                {
                    "resource": {
                        "resourceType": "Patient",
                        "identifier": [{"system": NATIONAL_ID_SYSTEM, "value": "29504150112345"}],
                        "address": [{"country": "US"}, {"country": "EG"}],
                    }
                }
            ],
        }
    )
    with pytest.raises(PatientNonEgyptianError):
        FhirValidator().validate(bundle)


def test_multiple_patients_first_failure_short_circuits() -> None:
    bad = _patient(nid=None)
    good = _patient()
    with pytest.raises(PatientMissingNationalIdError):
        FhirValidator().validate(_bundle(bad, good))


def test_non_patient_resources_skipped_silently() -> None:
    practitioner = {"resourceType": "Practitioner", "id": "p-1"}
    organisation = {"resourceType": "Organization", "name": "PayerCo"}
    FhirValidator().validate(_bundle(practitioner, organisation, _patient()))


def test_entry_wrapper_without_resource_skipped() -> None:
    bundle = json.dumps(
        {
            "resourceType": "Bundle",
            "type": "collection",
            "entry": [{}, {"fullUrl": "urn:uuid:abc"}, {"resource": _patient()}],
        }
    )
    FhirValidator().validate(bundle)


def test_non_array_entry_value_is_tolerated() -> None:
    # FHIR-spec says entry MUST be an array, but the validator only
    # asserts the Egyptian profile rules; a malformed entry shape passes
    # the structural check (and the deeper profile checks no-op).
    FhirValidator().validate('{"resourceType":"Bundle","type":"collection","entry":42}')


def test_national_id_value_not_validated_at_this_layer() -> None:
    # FhirValidator only asserts the slice exists; value-level validity
    # is EgyptianBundleValidator's job.
    FhirValidator().validate(_bundle(_patient(nid="not-a-real-nid")))
