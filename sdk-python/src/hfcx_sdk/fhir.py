"""FHIR Bundle validation against the Egyptian Implementation Guide.

Sprint P5 landed the hand-rolled :class:`hfcx_sdk.recipient.FhirValidator`
that enforces the rules that matter for HFCX's reject-on-receipt
behaviour (top-level Bundle, ``Bundle.type``, Patient National-ID
slice, Patient.address[0].country == ``EG``). Sprint P6 adds the
``bundled_ig_version`` helper so callers can introspect which release
of the platform's Egyptian IG package this build of the SDK ships
against.

The hand-rolled validator stays in :mod:`hfcx_sdk.recipient`; this
module only holds module-level constants and metadata helpers.
"""

from __future__ import annotations

from pathlib import Path
from typing import Final

#: System URI declared by the Egyptian IG for the National-ID identifier slice.
NATIONAL_ID_SYSTEM: Final[str] = "http://hcx-egypt.gov.eg/identifiers/national-id"

#: Sentinel returned by :func:`bundled_ig_version` when no IG package is bundled.
UNBUNDLED: Final[str] = "unbundled"

_PLATFORM_VERSION_FILE: Final[Path] = (
    Path(__file__).resolve().parent.parent.parent / "fhir-ig" / "PLATFORM_VERSION"
)


def bundled_ig_version() -> str:
    """Return the platform version recorded in ``fhir-ig/PLATFORM_VERSION``.

    Returns :data:`UNBUNDLED` when no IG package has been synced yet
    (the file is empty or missing). The value is the Git tag the IG
    tarball was downloaded from — e.g. ``"v1.0.0"`` once the platform
    cuts its first GA release.
    """
    try:
        text = _PLATFORM_VERSION_FILE.read_text(encoding="utf-8").strip()
    except OSError:
        return UNBUNDLED
    return text or UNBUNDLED


__all__ = ["NATIONAL_ID_SYSTEM", "UNBUNDLED", "bundled_ig_version"]
