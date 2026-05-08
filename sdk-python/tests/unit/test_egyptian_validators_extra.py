"""Extra edge-case coverage for the Egyptian validators.

The base ``test_egyptian_validators.py`` pins the cross-SDK invariant
behaviour; this module fills out the parametric corners (every
governorate code, leap-year boundaries, every mobile prefix, IBAN
mod-97 boundary, gender bit at every odd / even).
"""

from __future__ import annotations

import pytest

from hfcx_sdk.validators.egyptian_governorate import EgyptianGovernorate
from hfcx_sdk.validators.egyptian_iban import is_valid as iban_is_valid
from hfcx_sdk.validators.egyptian_national_id import (
    Gender,
)
from hfcx_sdk.validators.egyptian_national_id import (
    is_valid as nid_is_valid,
)
from hfcx_sdk.validators.egyptian_national_id import (
    parse as nid_parse,
)
from hfcx_sdk.validators.egyptian_phone import (
    is_valid as phone_is_valid,
)
from hfcx_sdk.validators.egyptian_phone import (
    normalise as phone_normalise,
)

# ── Governorate parametrics ─────────────────────────────────────────


@pytest.mark.parametrize("gov", list(EgyptianGovernorate))
def test_every_governorate_round_trips_through_from_code(gov: EgyptianGovernorate) -> None:
    assert EgyptianGovernorate.from_code(gov.code) is gov


@pytest.mark.parametrize("gov", list(EgyptianGovernorate))
def test_every_governorate_has_english_and_arabic_name(gov: EgyptianGovernorate) -> None:
    assert gov.english_name
    assert gov.arabic_name


@pytest.mark.parametrize("bad_code", ["", "0", "001", "0a", "  ", "00", "10", "20", "30", "99"])
def test_unrecognised_governorate_codes_return_none(bad_code: str) -> None:
    assert EgyptianGovernorate.from_code(bad_code) is None


# ── National-ID parametrics ─────────────────────────────────────────


@pytest.mark.parametrize("gov", list(EgyptianGovernorate))
def test_every_governorate_yields_a_valid_nid(gov: EgyptianGovernorate) -> None:
    # 1995-06-15, gender = male (digit 1), serial 1234.
    nid = f"295{0o6:02d}1{gov.code}12341"
    nid = "29506" + "15" + gov.code + "12341"
    assert nid_is_valid(nid), f"governorate {gov.name} should produce a valid NID"
    parsed = nid_parse(nid)
    assert parsed.governorate is gov


def test_century_2_yields_19xx_year_prefix() -> None:
    parsed = nid_parse("29504150112345")
    assert parsed.date_of_birth is not None
    assert parsed.date_of_birth.year == 1995


def test_century_3_yields_20xx_year_prefix() -> None:
    parsed = nid_parse("30312312112345")
    assert parsed.date_of_birth is not None
    assert parsed.date_of_birth.year == 2003


@pytest.mark.parametrize(
    "nid,expected_year,expected_month,expected_day",
    [
        ("20001100112341", 1900, 1, 10),  # earliest 19xx (1900-01-10)
        ("29912310112341", 1999, 12, 31),  # last day of 1900s
        ("30001100112341", 2000, 1, 10),  # first day of 2000s
        ("39912310112341", 2099, 12, 31),  # last day of 2000s
    ],
)
def test_year_boundary_dates_parse(
    nid: str, expected_year: int, expected_month: int, expected_day: int
) -> None:
    parsed = nid_parse(nid)
    assert parsed.valid
    assert parsed.date_of_birth is not None
    assert parsed.date_of_birth.year == expected_year
    assert parsed.date_of_birth.month == expected_month
    assert parsed.date_of_birth.day == expected_day


@pytest.mark.parametrize(
    "nid,expected_valid",
    [
        ("20002290112345", True),  # 1900 was a leap year? No — div by 100, not 400 → not leap
        ("30002290112345", True),  # 2000 IS a leap year (div by 400) → 02-29 valid
        ("30402290112345", True),  # 2004 — div by 4, not 100 → leap year
        ("31202290112345", True),  # 2012 leap year
        ("30502290112345", False),  # 2005 not leap
    ],
)
def test_leap_year_february_29_handled(nid: str, expected_valid: bool) -> None:
    # 1900 is NOT a leap year (Gregorian rule: div 100 unless div 400).
    if nid.startswith("20002290"):
        expected_valid = False
    assert nid_is_valid(nid) is expected_valid


@pytest.mark.parametrize(
    "serial_position_13,expected_gender",
    [
        ("1", Gender.MALE),
        ("3", Gender.MALE),
        ("5", Gender.MALE),
        ("7", Gender.MALE),
        ("9", Gender.MALE),
        ("0", Gender.FEMALE),
        ("2", Gender.FEMALE),
        ("4", Gender.FEMALE),
        ("6", Gender.FEMALE),
        ("8", Gender.FEMALE),
    ],
)
def test_every_gender_digit_resolves_to_correct_gender(
    serial_position_13: str, expected_gender: Gender
) -> None:
    # Position 13 (1-indexed) = index 12 (0-indexed) is the gender digit;
    # the last char (index 13) is the catalog checksum / unused trailing digit.
    nid = "295041501123" + serial_position_13 + "0"
    parsed = nid_parse(nid)
    assert parsed.valid
    assert parsed.gender is expected_gender


@pytest.mark.parametrize("century_digit", ["0", "1", "4", "5", "6", "7", "8", "9"])
def test_unsupported_century_digits_rejected(century_digit: str) -> None:
    nid = century_digit + "9504150112345"
    assert not nid_is_valid(nid)


def test_parse_returns_human_readable_reason_on_failure() -> None:
    assert "14 digits" in nid_parse("123").reason
    assert "non-digit" in nid_parse("2950415011234A").reason
    assert "century" in nid_parse("19504150112345").reason
    assert "governorate" in nid_parse("29504150999991").reason


# ── Phone parametrics ──────────────────────────────────────────────


@pytest.mark.parametrize("prefix", ["010", "011", "012", "015"])
def test_every_mobile_prefix_normalises(prefix: str) -> None:
    raw = prefix + "12345678"
    expected = "+201" + prefix[2:] + "12345678"
    assert phone_normalise(raw) == expected


@pytest.mark.parametrize(
    "raw",
    [
        "+201012345678",
        "  +201012345678  ",
        "+20 10 1234 5678",
        "+20-10-1234-5678",
        "0020-101-234-5678",
    ],
)
def test_phone_canonical_form_after_whitespace_and_punct_strip(raw: str) -> None:
    assert phone_normalise(raw) == "+201012345678"


@pytest.mark.parametrize(
    "bad",
    [
        "+201312345678",  # 013 not a real mobile prefix
        "+201712345678",  # 017 not assigned
        "+201812345678",  # 018 not assigned
        "+201912345678",  # 019 not assigned
        "+201412345678",  # 014 not assigned
        "+201612345678",  # 016 not assigned
        "+20101234567",  # one digit short (11 digits after +)
        "+2010123456789",  # one digit too long (13 digits after +)
        "+2010123456A8",  # contains letter
    ],
)
def test_invalid_phone_returns_none_from_normalise(bad: str) -> None:
    assert phone_normalise(bad) is None
    assert not phone_is_valid(bad)


# ── IBAN parametrics ───────────────────────────────────────────────


@pytest.mark.parametrize(
    "raw",
    [
        "EG380019000500000000263180002",
        "eg380019000500000000263180002",
        "EG38 0019 0005 0000 0000 2631 8000 2",
        "  EG380019000500000000263180002  ",
    ],
)
def test_every_canonical_iban_form_passes(raw: str) -> None:
    assert iban_is_valid(raw)


@pytest.mark.parametrize(
    "bad",
    [
        "EG380019000500000000263180001",  # check-digit perturbation by 1
        "EG380019000500000000263180004",  # check-digit perturbation by 2
        "EG370019000500000000263180002",  # leading check-digit altered
        "EG3800190005000000002631800OO",  # garbage tail
        "EG3800190005O00000002631800O2",  # letter where digit expected
        "GB380019000500000000263180002",  # not Egypt
        "XX380019000500000000263180002",  # bogus country
        " ",
        " EG ",
    ],
)
def test_iban_negatives_reject(bad: str) -> None:
    assert not iban_is_valid(bad)
