"""Validator and normaliser for Egyptian mobile phone numbers.

Sprint P6 lands the real implementation. The Java SDK's
``EgyptianPhoneValidator`` defines the canonical algorithm.
"""

from __future__ import annotations


def is_valid(phone: str | None) -> bool:  # pragma: no cover - P6
    """Return ``True`` iff ``phone`` is a valid Egyptian mobile number."""
    return normalise(phone) is not None


def normalise(phone: str | None) -> str | None:  # pragma: no cover - P6
    """Return the canonical ``+201XXXXXXXXX`` form, or ``None`` if invalid."""
    raise NotImplementedError("Sprint P6 lands the phone validator")


__all__ = ["is_valid", "normalise"]
