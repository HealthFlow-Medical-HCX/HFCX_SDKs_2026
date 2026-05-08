"""Validator for Egyptian IBANs.

Sister to the Java SDK's ``EgyptianIBANValidator``. CBE specification:
``EG`` + 2 check digits + 25 alphanumerics = 29 chars total. Must
pass the standard ISO 13616 mod-97 check.
"""

from __future__ import annotations

import re

_STRUCTURE = re.compile(r"^EG\d{2}[A-Z0-9]{25}$")


def is_valid(iban: str | None) -> bool:
    """Return ``True`` iff ``iban`` is a structurally valid Egyptian IBAN
    that passes the ISO 13616 mod-97 check.
    """
    if iban is None:
        return False
    cleaned = iban.replace(" ", "").upper()
    if not _STRUCTURE.match(cleaned):
        return False

    # ISO 13616 mod-97: move the first four characters to the end,
    # replace each letter with its 1-indexed position-in-the-alphabet
    # plus 9 (A → 10, B → 11, ..., Z → 35), parse as an integer, check
    # mod 97 == 1.
    reordered = cleaned[4:] + cleaned[:4]
    numeric_chars: list[str] = []
    for c in reordered:
        if c.isdigit():
            numeric_chars.append(c)
        else:
            numeric_chars.append(str(ord(c) - ord("A") + 10))
    return int("".join(numeric_chars)) % 97 == 1


__all__ = ["is_valid"]
