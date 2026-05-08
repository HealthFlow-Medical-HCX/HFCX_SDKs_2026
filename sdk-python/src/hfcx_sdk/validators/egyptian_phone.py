"""Validator and normaliser for Egyptian mobile phone numbers.

Sister to the Java SDK's ``EgyptianPhoneValidator``. Accepts the four
canonical mobile forms (international ``+``, double-zero, bare
country code, and local 0-prefixed) and returns the
``+201XXXXXXXXX`` canonical form. Mobile prefixes are 010, 011, 012,
015 (Vodafone, Etisalat, Orange, WE).
"""

from __future__ import annotations

import re

_NORMALISED = re.compile(r"^\+201[0125]\d{8}$")


def is_valid(phone: str | None) -> bool:
    """Return ``True`` iff ``phone`` is a valid Egyptian mobile number."""
    return normalise(phone) is not None


def normalise(phone: str | None) -> str | None:
    """Return the canonical ``+201XXXXXXXXX`` form, or ``None`` if invalid."""
    if phone is None:
        return None
    trimmed = phone.strip().replace(" ", "").replace("-", "")
    if trimmed.startswith("+20"):
        candidate = trimmed
    elif trimmed.startswith("0020"):
        candidate = "+20" + trimmed[4:]
    elif trimmed.startswith("20") and len(trimmed) == 12:
        candidate = "+" + trimmed
    elif trimmed.startswith("0") and len(trimmed) == 11:
        candidate = "+20" + trimmed[1:]
    else:
        return None
    return candidate if _NORMALISED.match(candidate) else None


__all__ = ["is_valid", "normalise"]
