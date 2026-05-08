"""The 27 Egyptian governorates and their National-ID prefix codes.

Source: CAPMAS. Codes are stable and identical across SDKs —
cross-SDK invariant. The Java SDK's ``EgyptianGovernorate`` enum
defines the canonical set; this enum mirrors it.
"""

from __future__ import annotations

from enum import Enum
from typing import Final


class EgyptianGovernorate(Enum):
    """Egyptian governorates with their two-digit National-ID prefix code."""

    CAIRO = ("01", "Cairo", "القاهرة")
    ALEXANDRIA = ("02", "Alexandria", "الإسكندرية")
    PORT_SAID = ("03", "Port Said", "بورسعيد")
    SUEZ = ("04", "Suez", "السويس")
    DAMIETTA = ("11", "Damietta", "دمياط")
    DAKAHLIA = ("12", "Dakahlia", "الدقهلية")
    SHARQIA = ("13", "Sharqia", "الشرقية")
    QALYUBIA = ("14", "Qalyubia", "القليوبية")
    KAFR_EL_SHEIKH = ("15", "Kafr El Sheikh", "كفر الشيخ")
    GHARBIA = ("16", "Gharbia", "الغربية")
    MONUFIA = ("17", "Monufia", "المنوفية")
    BEHEIRA = ("18", "Beheira", "البحيرة")
    ISMAILIA = ("19", "Ismailia", "الإسماعيلية")
    GIZA = ("21", "Giza", "الجيزة")
    BENI_SUEF = ("22", "Beni Suef", "بني سويف")
    FAIYUM = ("23", "Faiyum", "الفيوم")
    MINYA = ("24", "Minya", "المنيا")
    ASYUT = ("25", "Asyut", "أسيوط")
    SOHAG = ("26", "Sohag", "سوهاج")
    QENA = ("27", "Qena", "قنا")
    ASWAN = ("28", "Aswan", "أسوان")
    LUXOR = ("29", "Luxor", "الأقصر")
    RED_SEA = ("31", "Red Sea", "البحر الأحمر")
    NEW_VALLEY = ("32", "New Valley", "الوادي الجديد")
    MATRUH = ("33", "Matruh", "مطروح")
    NORTH_SINAI = ("34", "North Sinai", "شمال سيناء")
    SOUTH_SINAI = ("35", "South Sinai", "جنوب سيناء")

    @property
    def code(self) -> str:
        """Two-digit National-ID prefix code, e.g. ``"01"`` for Cairo."""
        return self.value[0]

    @property
    def english_name(self) -> str:
        return self.value[1]

    @property
    def arabic_name(self) -> str:
        return self.value[2]

    @classmethod
    def from_code(cls, code: str | None) -> EgyptianGovernorate | None:
        """Look up a governorate by its two-digit National-ID prefix."""
        if code is None:
            return None
        return _BY_CODE.get(code)


_BY_CODE: Final[dict[str, EgyptianGovernorate]] = {g.code: g for g in EgyptianGovernorate}


__all__ = ["EgyptianGovernorate"]
