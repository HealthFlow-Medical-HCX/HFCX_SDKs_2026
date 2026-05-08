"""Tests for :mod:`hfcx_sdk.fhir` module-level helpers."""

from __future__ import annotations

from pathlib import Path

import pytest

from hfcx_sdk import bundled_ig_version
from hfcx_sdk.fhir import (
    _PLATFORM_VERSION_FILE,
    NATIONAL_ID_SYSTEM,
    UNBUNDLED,
)
from hfcx_sdk.fhir import (
    bundled_ig_version as fn,
)


def test_national_id_system_uri_pinned() -> None:
    assert NATIONAL_ID_SYSTEM == "http://hcx-egypt.gov.eg/identifiers/national-id"


def test_unbundled_sentinel_pinned() -> None:
    assert UNBUNDLED == "unbundled"


def test_returns_unbundled_when_platform_version_file_is_blank() -> None:
    # Real fhir-ig/PLATFORM_VERSION ships blank until P6+ syncs the IG.
    assert fn() == UNBUNDLED
    assert bundled_ig_version() == UNBUNDLED


def test_returns_unbundled_when_file_missing(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    missing = tmp_path / "nope" / "PLATFORM_VERSION"
    monkeypatch.setattr("hfcx_sdk.fhir._PLATFORM_VERSION_FILE", missing)
    assert fn() == UNBUNDLED


def test_returns_recorded_version_when_file_populated(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    fake = tmp_path / "PLATFORM_VERSION"
    fake.write_text("v1.0.0\n", encoding="utf-8")
    monkeypatch.setattr("hfcx_sdk.fhir._PLATFORM_VERSION_FILE", fake)
    assert fn() == "v1.0.0"


def test_strips_surrounding_whitespace(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    fake = tmp_path / "PLATFORM_VERSION"
    fake.write_text("\n  v2.3.4\t\n", encoding="utf-8")
    monkeypatch.setattr("hfcx_sdk.fhir._PLATFORM_VERSION_FILE", fake)
    assert fn() == "v2.3.4"


def test_path_points_at_repo_fhir_ig_directory() -> None:
    # Sanity: the constant is anchored to fhir-ig/ next to src/.
    assert _PLATFORM_VERSION_FILE.name == "PLATFORM_VERSION"
    assert _PLATFORM_VERSION_FILE.parent.name == "fhir-ig"
