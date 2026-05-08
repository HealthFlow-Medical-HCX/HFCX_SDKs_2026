"""Protocol header tests — byte-level parity with the Java SDK.

Sister to ``ProtocolHeadersTest`` on the Java side. The exact wire
strings here must NOT diverge between SDKs.
"""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from hfcx_sdk import protocol


def test_header_names_match_integration_guide_24_5_gap_7_form() -> None:
    # Pinned. Any change here is a coordinated cross-SDK breaking release.
    assert protocol.SENDER_CODE == "x-hcx-sender_code"
    assert protocol.RECIPIENT_CODE == "x-hcx-recipient_code"
    assert protocol.CORRELATION_ID == "x-hcx-correlation_id"
    assert protocol.TIMESTAMP == "x-hcx-timestamp"
    # Note: api-call-id is fully hyphenated, distinct from the others.
    assert protocol.API_CALL_ID == "x-hcx-api-call-id"


def test_build_emits_expected_keys_in_deterministic_order() -> None:
    headers = protocol.build(
        "myhospital@hcx-egypt",
        "payerco@hcx-egypt",
        "11111111-2222-4333-8444-555555555555",
        datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc),
        "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    )

    assert list(headers.keys()) == [
        "x-hcx-sender_code",
        "x-hcx-recipient_code",
        "x-hcx-correlation_id",
        "x-hcx-timestamp",
        "x-hcx-api-call-id",
    ], "header order must be stable so cross-SDK byte-level comparisons succeed"


def test_timestamp_is_iso_8601_with_z_suffix() -> None:
    headers = protocol.build(
        "sender@hcx-egypt",
        "recipient@hcx-egypt",
        "corr-1",
        datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc),
        "call-1",
    )
    assert headers[protocol.TIMESTAMP] == "2026-05-08T19:42:18Z"


def test_naive_datetime_is_treated_as_utc() -> None:
    naive = datetime(2026, 5, 8, 19, 42, 18)
    headers = protocol.build("s", "r", "c", naive, "a")
    assert headers[protocol.TIMESTAMP] == "2026-05-08T19:42:18Z"


def test_values_are_preserved_exactly() -> None:
    headers = protocol.build(
        "myhospital@hcx-egypt",
        "payerco@hcx-egypt",
        "corr-xyz",
        datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc),
        "call-123",
    )
    assert headers[protocol.SENDER_CODE] == "myhospital@hcx-egypt"
    assert headers[protocol.RECIPIENT_CODE] == "payerco@hcx-egypt"
    assert headers[protocol.CORRELATION_ID] == "corr-xyz"
    assert headers[protocol.API_CALL_ID] == "call-123"


def test_result_is_immutable() -> None:
    headers = protocol.build(
        "s", "r", "c", datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc), "a"
    )
    with pytest.raises(TypeError):
        headers["x-hcx-injected"] = "boom"  # type: ignore[index]


def test_rejects_none_arguments() -> None:
    t = datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc)
    with pytest.raises(TypeError):
        protocol.build(None, "r", "c", t, "a")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        protocol.build("s", None, "c", t, "a")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        protocol.build("s", "r", None, t, "a")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        protocol.build("s", "r", "c", None, "a")  # type: ignore[arg-type]
    with pytest.raises(TypeError):
        protocol.build("s", "r", "c", t, None)  # type: ignore[arg-type]


def test_ordered_header_names_matches_build_output() -> None:
    headers = protocol.build(
        "s", "r", "c", datetime(2026, 5, 8, 19, 42, 18, tzinfo=timezone.utc), "a"
    )
    assert tuple(headers.keys()) == protocol.ORDERED_HEADER_NAMES
