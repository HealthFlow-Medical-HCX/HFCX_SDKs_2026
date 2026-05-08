"""Validator for Egyptian IBANs.

Sprint P6 lands the real implementation. The Java SDK's
``EgyptianIBANValidator`` defines the canonical algorithm: 29-char
structure (``EG`` + 2 check digits + 25 alphanumerics) plus ISO
13616 mod-97 check.
"""

from __future__ import annotations


def is_valid(iban: str | None) -> bool:  # pragma: no cover - P6
    """Return ``True`` iff ``iban`` is a structurally valid Egyptian IBAN
    that passes the ISO 13616 mod-97 check.
    """
    raise NotImplementedError("Sprint P6 lands the IBAN validator")


__all__ = ["is_valid"]
