"""Egyptian field-validator tests.

Sister to the Java SDK's ``EgyptianValidatorsTest``. Cross-SDK
invariant: same input → same accept/reject decision.
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

# ── Governorate ─────────────────────────────────────────────────────


def test_governorate_enum_has_exactly_27_entries() -> None:
    assert len(list(EgyptianGovernorate)) == 27


def test_governorate_lookup_by_code_finds_known_prefixes() -> None:
    assert EgyptianGovernorate.from_code("01") is EgyptianGovernorate.CAIRO
    assert EgyptianGovernorate.from_code("21") is EgyptianGovernorate.GIZA
    assert EgyptianGovernorate.from_code("35") is EgyptianGovernorate.SOUTH_SINAI
    assert EgyptianGovernorate.from_code("00") is None
    assert EgyptianGovernorate.from_code(None) is None
    assert EgyptianGovernorate.from_code("99") is None


# ── National ID ─────────────────────────────────────────────────────


def test_nid_happy_paths() -> None:
    assert nid_is_valid("29504150112345")  # 1995-04-15, Cairo
    assert nid_is_valid("30312312112340")  # 2003-12-31, Giza
    assert nid_is_valid("29902280298765")  # 1999-02-28, Alexandria


def test_nid_rich_result_exposes_parsed_fields() -> None:
    r = nid_parse("29504150112345")
    assert r.valid
    assert r.date_of_birth is not None
    assert r.date_of_birth.year == 1995
    assert r.date_of_birth.month == 4
    assert r.date_of_birth.day == 15
    assert r.governorate is EgyptianGovernorate.CAIRO


def test_nid_rejects_wrong_length() -> None:
    assert not nid_is_valid("123")
    assert not nid_is_valid("295041501123450")  # 15 digits
    assert not nid_is_valid("2950415011234")  # 13 digits
    assert not nid_is_valid("")
    assert not nid_is_valid(None)


def test_nid_rejects_non_digit() -> None:
    assert not nid_is_valid("2950415011234A")
    assert not nid_is_valid("29504X50112345")


def test_nid_rejects_unknown_century_digit() -> None:
    assert not nid_is_valid("19504150112345")  # century 1
    assert not nid_is_valid("49504150112345")  # century 4


def test_nid_rejects_impossible_date() -> None:
    assert not nid_is_valid("29513320112345")  # month 13
    assert not nid_is_valid("29502310112345")  # Feb 31
    assert not nid_is_valid("29502290112345")  # 1995 not a leap year
    assert not nid_is_valid("29504310112345")  # April 31


def test_nid_rejects_unknown_governorate() -> None:
    assert not nid_is_valid("29504150512345")  # gov 05
    assert not nid_is_valid("29504159912345")  # gov 99


def test_nid_gender_inferred_from_serial_digit() -> None:
    # Position 13 (1-indexed) is the gender digit: odd → male, even → female.
    male = nid_parse("29504150112355")
    female = nid_parse("29504150112365")
    assert male.gender is Gender.MALE
    assert female.gender is Gender.FEMALE


# ── Phone ───────────────────────────────────────────────────────────


def test_phone_accepts_all_four_canonical_forms() -> None:
    assert phone_normalise("+201012345678") == "+201012345678"
    assert phone_normalise("00201012345678") == "+201012345678"
    assert phone_normalise("201012345678") == "+201012345678"
    assert phone_normalise("01012345678") == "+201012345678"


def test_phone_accepts_all_four_mobile_prefixes() -> None:
    assert phone_is_valid("01012345678")
    assert phone_is_valid("01112345678")
    assert phone_is_valid("01212345678")
    assert phone_is_valid("01512345678")


def test_phone_strips_whitespace_and_hyphens() -> None:
    assert phone_normalise("+20 10 1234 5678") == "+201012345678"
    assert phone_normalise("0101-234-5678") == "+201012345678"


@pytest.mark.parametrize(
    "bad",
    [
        None,
        "",
        "01312345678",  # 013 not a mobile prefix
        "0101234567",  # 10 digits, too short
        "010123456789",  # 12 digits, too long
        "11012345678",  # doesn't start with 0/+/2
        "+30101234567",  # wrong country code
    ],
)
def test_phone_rejects_bad_inputs(bad: str | None) -> None:
    assert not phone_is_valid(bad)


# ── IBAN ────────────────────────────────────────────────────────────


def test_iban_accepts_known_valid_example() -> None:
    # Reference example from public CBE documentation.
    assert iban_is_valid("EG380019000500000000263180002")


def test_iban_is_case_insensitive_and_strips_spaces() -> None:
    assert iban_is_valid("eg38 0019 0005 0000 0000 2631 8000 2")


@pytest.mark.parametrize(
    "bad",
    [
        None,
        "",
        "EG380019000500000000263180003",  # bad check digits
        "FR380019000500000000263180002",  # wrong country
        "EG3800190005000000002631800022",  # 30 chars
        "EG380019000500000000263180",  # 26 chars
        "EG3800190005000000002631800!2",  # bad char
    ],
)
def test_iban_rejects_bad_inputs(bad: str | None) -> None:
    assert not iban_is_valid(bad)
