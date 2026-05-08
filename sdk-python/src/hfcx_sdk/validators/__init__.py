"""Egyptian field validators.

Sprint P6 lands the implementations. Public surface declared in P1
(this module's exports) so dependents can import the names today.
"""

from __future__ import annotations

from hfcx_sdk.validators.egyptian_governorate import EgyptianGovernorate
from hfcx_sdk.validators.egyptian_iban import is_valid as is_valid_iban
from hfcx_sdk.validators.egyptian_national_id import (
    Gender,
    NationalIdResult,
)
from hfcx_sdk.validators.egyptian_national_id import (
    is_valid as is_valid_national_id,
)
from hfcx_sdk.validators.egyptian_national_id import (
    parse as parse_national_id,
)
from hfcx_sdk.validators.egyptian_phone import (
    is_valid as is_valid_phone,
)
from hfcx_sdk.validators.egyptian_phone import (
    normalise as normalise_phone,
)

__all__ = [
    "EgyptianGovernorate",
    "Gender",
    "NationalIdResult",
    "is_valid_iban",
    "is_valid_national_id",
    "is_valid_phone",
    "normalise_phone",
    "parse_national_id",
]
