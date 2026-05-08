"""Structural validator for Egyptian National-ID numbers.

Sister to the Java SDK's ``EgyptianNationalIDValidator``. The
algorithm decodes the 14-digit format into date-of-birth, governorate,
and gender; it does NOT verify the closing checksum digit (no single
authoritative public algorithm — drift across SDKs would be a
cross-SDK bug). Cross-SDK invariant: same input → same accept/reject
decision.
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
    """Rich validation result.

    :param valid: ``True`` iff the structural checks pass.
    :param reason: human-readable description (``"ok"`` on success).
    :param date_of_birth: parsed Gregorian date, or ``None``.
    :param governorate: parsed governorate, or ``None``.
    :param gender: parsed gender, or ``None``.
    """

    valid: bool
    reason: str
    date_of_birth: date | None = None
    governorate: EgyptianGovernorate | None = None
    gender: Gender | None = None


def is_valid(national_id: str | None) -> bool:
    """Return ``True`` iff ``national_id`` is structurally valid."""
    return parse(national_id).valid


def parse(national_id: str | None) -> NationalIdResult:
    """Decode a National ID into a :class:`NationalIdResult`."""
    if national_id is None or len(national_id) != 14:
        return NationalIdResult(False, "must be exactly 14 digits")
    if not national_id.isdigit():
        return NationalIdResult(False, "contains a non-digit character")

    century_digit = national_id[0]
    if century_digit == "2":
        year_prefix = 1900
    elif century_digit == "3":
        year_prefix = 2000
    else:
        return NationalIdResult(False, f"century digit must be 2 or 3 (got {century_digit!r})")

    year = year_prefix + int(national_id[1:3])
    month = int(national_id[3:5])
    day = int(national_id[5:7])
    try:
        dob = date(year, month, day)
    except ValueError:
        return NationalIdResult(
            False,
            f"date of birth {year}-{month}-{day} is not a real Gregorian date",
        )

    gov_code = national_id[7:9]
    governorate = EgyptianGovernorate.from_code(gov_code)
    if governorate is None:
        return NationalIdResult(False, f"governorate code {gov_code!r} is not recognised")

    # Position 13 (1-indexed) is the gender digit. Index 12 (0-indexed).
    gender_digit = int(national_id[12])
    gender = Gender.MALE if gender_digit % 2 == 1 else Gender.FEMALE

    return NationalIdResult(
        valid=True,
        reason="ok",
        date_of_birth=dob,
        governorate=governorate,
        gender=gender,
    )


__all__ = ["Gender", "NationalIdResult", "is_valid", "parse"]
