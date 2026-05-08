"""FHIR Bundle validation against the Egyptian Implementation Guide.

Sprint P6 lands the real implementation. The Java SDK ships a hand-
rolled validator (avoiding the heavyweight FHIR validator dep); the
Python SDK will follow the same pattern unless ``fhir.resources``
makes the full IG-profile route cheap enough to take.
"""

from __future__ import annotations

from typing import Final

#: System URI declared by the Egyptian IG for the National-ID identifier slice.
NATIONAL_ID_SYSTEM: Final[str] = "http://hcx-egypt.gov.eg/identifiers/national-id"


def validate(bundle_json: str) -> None:  # pragma: no cover - P6
    """Validate a FHIR Bundle against the Egyptian IG profile.

    :raises hfcx_sdk.exceptions.NotABundleError: if the top-level
        resource is not a Bundle.
    :raises hfcx_sdk.exceptions.BundleMissingTypeError: if
        ``Bundle.type`` is missing.
    :raises hfcx_sdk.exceptions.PatientMissingNationalIdError: if a
        ``Patient`` entry has no identifier with the National-ID
        system URI.
    :raises hfcx_sdk.exceptions.PatientNonEgyptianError: if a
        ``Patient`` entry's first address is not in ``EG``.
    :raises hfcx_sdk.exceptions.BadFhirJsonError: if the payload is
        not valid JSON.
    """
    raise NotImplementedError("Sprint P6 lands the FHIR validator")


__all__ = ["NATIONAL_ID_SYSTEM", "validate"]
