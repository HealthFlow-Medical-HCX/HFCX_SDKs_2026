"""Sprint P1 acceptance criterion: ``__version__`` is exported."""

from __future__ import annotations

import re

import hfcx_sdk


def test_version_attribute_exists() -> None:
    assert isinstance(hfcx_sdk.__version__, str)


def test_version_starts_at_alpha_zero() -> None:
    # P1 acceptance: the bootstrap ships as 0.1.0a0 on Test PyPI as a
    # sanity check before the real release line opens.
    assert hfcx_sdk.__version__ == "0.1.0a0"


def test_version_is_semver_compatible() -> None:
    # PEP 440 / semver-ish format: digits.digits.digits with optional
    # alpha/beta/rc/dev suffix.
    assert re.match(r"^\d+\.\d+\.\d+([abrcdev]+\d+)?$", hfcx_sdk.__version__)


def test_public_api_re_exports_error_taxonomy() -> None:
    # The names below are the cross-SDK invariant public surface for
    # the error taxonomy. Removing any of them is a breaking change.
    for name in (
        "ErrorCode",
        "HfcxError",
        "ProtocolError",
        "BusinessError",
        "TechnicalError",
        "AuthenticationError",
    ):
        assert hasattr(hfcx_sdk, name), f"hfcx_sdk does not export {name}"
