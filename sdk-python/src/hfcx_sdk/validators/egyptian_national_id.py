"""Structural validator for Egyptian National-ID numbers.

Sprint P6 lands the real implementation. The Java SDK's
``EgyptianNationalIDValidator`` defines the canonical algorithm;
this module mirrors it. Cross-SDK invariant — same input must
produce the same accept/reject decision in every language.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from hfcx_sdk.validators.egyptian_governorate import EgyptianGovernorate


class Gender(Enum):
    """Gender encoded in the National ID's serial digit (odd = male, even = female)."""

    MALE = "MALE"
    FEMALE = "FEMALE"


@dataclass(frozen=True, slots=True)
class NationalIdResult:
    """Rich validation result for an Egyptian National ID.

    :param valid: ``True`` iff the structural checks all pass.
    :param reason: human-readable description of the failure (or ``"ok"``).
    :param date_of_birth: parsed Gregorian date of birth, or ``None``
        if the ID failed validation before the date check.
    :param governorate: parsed governorate, or ``None``.
    :param gender: parsed gender, or ``None``.
    """

    valid: bool
    reason: str
    date_of_birth: date | None = None
    governorate: EgyptianGovernorate | None = None
    gender: Gender | None = None


def is_valid(national_id: str | None) -> bool:  # pragma: no cover - P6
    """Return ``True`` iff ``national_id`` is structurally valid."""
    return parse(national_id).valid


def parse(national_id: str | None) -> NationalIdResult:  # pragma: no cover - P6
    """Decode a National ID into a :class:`NationalIdResult`."""
    raise NotImplementedError("Sprint P6 lands the National-ID validator")


__all__ = ["Gender", "NationalIdResult", "is_valid", "parse"]
