#!/usr/bin/env python3
"""Cross-SDK parity audit.

Verifies that every public symbol exported by an SDK has a row in
``docs/CROSS_SDK_PARITY.md`` and that every row claims the SDK is
implemented (✅) — pre-flight check for cutting a release.

Run:

::

    python scripts/audit_parity.py --sdk python

Exit code 0 on success, 1 on parity drift.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Final

REPO_ROOT: Final[Path] = Path(__file__).resolve().parent.parent
PARITY_PATH: Final[Path] = REPO_ROOT / "docs" / "CROSS_SDK_PARITY.md"

#: Symbols deliberately omitted from the parity table (test helpers,
#: exception subclasses tracked under aggregate "27-entry catalog"
#: rows, etc).
PYTHON_PARITY_EXEMPT: Final[frozenset[str]] = frozenset(
    {
        # Catalog rows are aggregated; per-subclass parity is implied.
        "AuthenticationError",
        "ErrorCode",
        "HfcxError",
        "ProtocolError",
        "BusinessError",
        "TechnicalError",
        # Module-internal helper, surfaced because it's used by the
        # encryptor and registry and tests want to type against it.
        "AsyncRecipientCertResolver",
        # Protocol header constants are covered by row 22.
        "correlation_id_scope",
        # Version identifier covered by row 7.
        "__version__",
    }
)


def _expected_columns(sdk: str) -> tuple[int, ...]:
    """Per-row column index of the SDK status cell after split('|').

    Layout: ``# | Capability | Java | Python | .NET | JavaScript`` →
    java=2, python=3, dotnet=4, javascript=5 (0-indexed).
    """
    if sdk == "java":
        return (2,)
    if sdk == "python":
        return (3,)
    if sdk in {"dotnet", ".net"}:
        return (4,)
    if sdk in {"javascript", "js"}:
        return (5,)
    raise ValueError(f"unknown sdk: {sdk!r}")


def _public_symbols_python() -> list[str]:
    """Read ``hfcx_sdk.__all__`` without importing the package."""
    init_text = (
        REPO_ROOT / "sdk-python" / "src" / "hfcx_sdk" / "__init__.py"
    ).read_text(encoding="utf-8")
    match = re.search(r"__all__\s*=\s*\[(.*?)\]", init_text, re.DOTALL)
    if not match:
        raise RuntimeError("hfcx_sdk/__init__.py does not declare __all__")
    body = match.group(1)
    names = re.findall(r'"([^"]+)"', body)
    return sorted(set(names))


def _parity_rows() -> list[list[str]]:
    """Return parity-table rows as lists of stripped cell strings."""
    text = PARITY_PATH.read_text(encoding="utf-8")
    rows: list[list[str]] = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        # Skip header / separator rows.
        if not cells or not cells[0].isdigit():
            continue
        rows.append(cells)
    return rows


def _row_capability_text(row: list[str]) -> str:
    return row[1] if len(row) > 1 else ""


def _row_sdk_cell(row: list[str], sdk_col: int) -> str:
    return row[sdk_col] if len(row) > sdk_col else ""


def audit(sdk: str) -> int:
    columns = _expected_columns(sdk)
    rows = _parity_rows()
    if not rows:
        print(f"ERROR: no parity rows parsed from {PARITY_PATH}", file=sys.stderr)
        return 1

    issues: list[str] = []

    # Rule 1: every row must mark this SDK as ✅.
    for row in rows:
        cell = _row_sdk_cell(row, columns[0])
        if not cell.startswith("✅"):
            issues.append(
                f"row {row[0]} ({_row_capability_text(row)!r}) is not ✅ "
                f"for {sdk}: {cell!r}"
            )

    if sdk == "python":
        # Rule 2: every public symbol in __all__ has at least one row
        # mentioning it (loose substring check — the table cells use
        # backtick-fenced symbol names).
        symbols = _public_symbols_python()
        flat_capability = "\n".join(_row_capability_text(r) for r in rows)
        # Concatenate every Python column cell as well — some rows
        # name the Python symbol only in the Python cell, not in the
        # capability label.
        flat_python = "\n".join(_row_sdk_cell(r, columns[0]) for r in rows)
        haystack = flat_capability + "\n" + flat_python
        for symbol in symbols:
            if symbol in PYTHON_PARITY_EXEMPT:
                continue
            if symbol not in haystack:
                issues.append(
                    f"public symbol {symbol!r} is in hfcx_sdk.__all__ "
                    f"but has no parity-table row"
                )

    if issues:
        print(f"\n{len(issues)} parity issue(s) found for sdk={sdk!r}:", file=sys.stderr)
        for issue in issues:
            print(f"  - {issue}", file=sys.stderr)
        return 1

    row_count = len(rows)
    print(f"OK — {sdk} column has {row_count} ✅ rows; no drift.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Cross-SDK parity audit")
    parser.add_argument(
        "--sdk",
        choices=("java", "python", "dotnet", "javascript"),
        required=True,
        help="Which SDK column to audit.",
    )
    args = parser.parse_args()
    return audit(args.sdk)


if __name__ == "__main__":
    raise SystemExit(main())
